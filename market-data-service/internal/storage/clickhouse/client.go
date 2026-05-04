package clickhouse

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/awesoma/trumpinvestitions/market-data-service/internal/domain"
)

type Client struct {
	baseURL         string
	database        string
	user            string
	password        string
	httpClient      *http.Client
	queryTimeout    time.Duration
	snapshotSamples int
}

type jsonResponse[T any] struct {
	Data []T `json:"data"`
}

func New(baseURL, database, user, password string, httpClient *http.Client, queryTimeout time.Duration, snapshotSamples int) *Client {
	return &Client{
		baseURL:         baseURL,
		database:        database,
		user:            user,
		password:        password,
		httpClient:      httpClient,
		queryTimeout:    queryTimeout,
		snapshotSamples: snapshotSamples,
	}
}

func (c *Client) Ping(ctx context.Context) error {
	var rows []struct {
		Value int `json:"value"`
	}
	if err := selectJSON(ctx, c, "SELECT 1 AS value FORMAT JSON", &rows); err != nil {
		return err
	}
	if len(rows) != 1 || rows[0].Value != 1 {
		return fmt.Errorf("unexpected ping response")
	}
	return nil
}

func (c *Client) ListSymbols(ctx context.Context, query string, limit, offset int) ([]string, int, error) {
	filter := ""
	if query != "" {
		filter = fmt.Sprintf("WHERE positionCaseInsensitive(symbol, %s) > 0", quoteString(query))
	}

	var totalRows []struct {
		Total int `json:"total"`
	}
	countSQL := fmt.Sprintf(`
		SELECT count() AS total
		FROM (
			SELECT DISTINCT symbol
			FROM quotes
			%s
		)
		FORMAT JSON
	`, filter)
	if err := selectJSON(ctx, c, countSQL, &totalRows); err != nil {
		return nil, 0, err
	}

	var symbolRows []struct {
		Symbol string `json:"symbol"`
	}
	listSQL := fmt.Sprintf(`
		SELECT symbol
		FROM (
			SELECT DISTINCT symbol
			FROM quotes
			%s
		)
		ORDER BY symbol
		LIMIT %d OFFSET %d
		FORMAT JSON
	`, filter, limit, offset)
	if err := selectJSON(ctx, c, listSQL, &symbolRows); err != nil {
		return nil, 0, err
	}

	symbols := make([]string, 0, len(symbolRows))
	for _, row := range symbolRows {
		symbols = append(symbols, row.Symbol)
	}

	total := 0
	if len(totalRows) > 0 {
		total = totalRows[0].Total
	}
	return symbols, total, nil
}

func (c *Client) SymbolExists(ctx context.Context, symbol string) (bool, error) {
	var rows []struct {
		Count int `json:"count"`
	}
	sql := fmt.Sprintf("SELECT count() AS count FROM quotes WHERE symbol = %s FORMAT JSON", quoteString(symbol))
	if err := selectJSON(ctx, c, sql, &rows); err != nil {
		return false, err
	}
	return len(rows) > 0 && rows[0].Count > 0, nil
}

func (c *Client) GetLatestQuotes(ctx context.Context, symbols []string) ([]domain.QuoteSnapshot, error) {
	if len(symbols) == 0 {
		return []domain.QuoteSnapshot{}, nil
	}

	sql := fmt.Sprintf(`
		SELECT
			symbol,
			argMax(bid_price, tuple(event_time_ns, sequence)) AS bid,
			argMax(ask_price, tuple(event_time_ns, sequence)) AS ask,
			argMax(last_price, tuple(event_time_ns, sequence)) AS last,
			argMax(event_time_ns, tuple(event_time_ns, sequence)) AS latest_event_time_ns,
			argMin(last_price, tuple(event_time_ns, sequence)) AS open,
			max(last_price) AS high,
			min(last_price) AS low,
			argMax(last_price, tuple(event_time_ns, sequence)) AS close,
			toInt64(round(sum(last_size))) AS volume
		FROM quotes
		WHERE symbol IN (%s)
		GROUP BY symbol
		ORDER BY symbol
		FORMAT JSON
	`, quotedList(symbols))

	var rows []struct {
		Symbol      string  `json:"symbol"`
		Bid         float64 `json:"bid"`
		Ask         float64 `json:"ask"`
		Last        float64 `json:"last"`
		EventTimeNS uint64  `json:"latest_event_time_ns"`
		Open        float64 `json:"open"`
		High        float64 `json:"high"`
		Low         float64 `json:"low"`
		Close       float64 `json:"close"`
		Volume      int64   `json:"volume"`
	}
	if err := selectJSON(ctx, c, sql, &rows); err != nil {
		return nil, err
	}

	snapshots := make([]domain.QuoteSnapshot, 0, len(rows))
	for _, row := range rows {
		open := row.Open
		high := row.High
		low := row.Low
		closeValue := row.Close
		volume := row.Volume

		snapshots = append(snapshots, domain.QuoteSnapshot{
			Symbol:      row.Symbol,
			Bid:         row.Bid,
			Ask:         row.Ask,
			Last:        row.Last,
			Open:        &open,
			High:        &high,
			Low:         &low,
			Close:       &closeValue,
			Volume:      &volume,
			EventTimeNS: row.EventTimeNS,
		})
	}

	return snapshots, nil
}

func (c *Client) GetCandles(ctx context.Context, symbol string, from, to time.Time, interval string, limit int) ([]domain.CandlePoint, error) {
	bucketNS, err := intervalToNS(interval)
	if err != nil {
		return nil, err
	}

	sql := fmt.Sprintf(`
		SELECT
			intDiv(event_time_ns, %d) * %d AS bucket_ns,
			argMin(last_price, tuple(event_time_ns, sequence)) AS open,
			max(last_price) AS high,
			min(last_price) AS low,
			argMax(last_price, tuple(event_time_ns, sequence)) AS close,
			toInt64(round(sum(last_size))) AS volume
		FROM quotes
		WHERE symbol = %s
		  AND event_time_ns >= %d
		  AND event_time_ns <= %d
		GROUP BY bucket_ns
		ORDER BY bucket_ns
		LIMIT %d
		FORMAT JSON
	`, bucketNS, bucketNS, quoteString(symbol), toUnixNS(from), toUnixNS(to), limit)

	var rows []struct {
		BucketNS uint64  `json:"bucket_ns"`
		Open     float64 `json:"open"`
		High     float64 `json:"high"`
		Low      float64 `json:"low"`
		Close    float64 `json:"close"`
		Volume   int64   `json:"volume"`
	}
	if err := selectJSON(ctx, c, sql, &rows); err != nil {
		return nil, err
	}

	result := make([]domain.CandlePoint, 0, len(rows))
	for _, row := range rows {
		result = append(result, domain.CandlePoint{
			TimestampNS: row.BucketNS,
			Open:        row.Open,
			High:        row.High,
			Low:         row.Low,
			Close:       row.Close,
			Volume:      row.Volume,
		})
	}
	return result, nil
}

func (c *Client) GetOrderBook(ctx context.Context, symbol string, depth int) ([]domain.OrderBookLevelSnapshot, []domain.OrderBookLevelSnapshot, error) {
	var (
		bids    []domain.OrderBookLevelSnapshot
		asks    []domain.OrderBookLevelSnapshot
		bidErr  error
		askErr  error
		wg      sync.WaitGroup
	)
	wg.Add(2)
	go func() {
		defer wg.Done()
		bids, bidErr = c.getOrderBookSide(ctx, symbol, "bid", depth)
	}()
	go func() {
		defer wg.Done()
		asks, askErr = c.getOrderBookSide(ctx, symbol, "ask", depth)
	}()
	wg.Wait()
	if bidErr != nil {
		return nil, nil, bidErr
	}
	if askErr != nil {
		return nil, nil, askErr
	}
	return bids, asks, nil
}

func (c *Client) getOrderBookSide(ctx context.Context, symbol, side string, depth int) ([]domain.OrderBookLevelSnapshot, error) {
	priceColumn := "bid_price"
	sizeColumn := "bid_size"
	order := "DESC"
	if side == "ask" {
		priceColumn = "ask_price"
		sizeColumn = "ask_size"
		order = "ASC"
	}

	sql := fmt.Sprintf(`
		SELECT
			price,
			toInt64(round(sum(quantity))) AS quantity
		FROM (
			SELECT
				%s AS price,
				%s AS quantity
			FROM quotes
			WHERE symbol = %s
			ORDER BY event_time_ns DESC, sequence DESC
			LIMIT %d
		)
		GROUP BY price
		ORDER BY price %s
		LIMIT %d
		FORMAT JSON
	`, priceColumn, sizeColumn, quoteString(symbol), c.snapshotSamples, order, depth)

	var rows []struct {
		Price    float64 `json:"price"`
		Quantity int64   `json:"quantity"`
	}
	if err := selectJSON(ctx, c, sql, &rows); err != nil {
		return nil, err
	}

	levels := make([]domain.OrderBookLevelSnapshot, 0, len(rows))
	for _, row := range rows {
		levels = append(levels, domain.OrderBookLevelSnapshot{
			Price:    row.Price,
			Quantity: row.Quantity,
		})
	}

	if side == "bid" {
		sort.Slice(levels, func(i, j int) bool {
			return levels[i].Price > levels[j].Price
		})
	} else {
		sort.Slice(levels, func(i, j int) bool {
			return levels[i].Price < levels[j].Price
		})
	}

	return levels, nil
}

func selectJSON[T any](ctx context.Context, client *Client, sql string, target *[]T) error {
	queryCtx, cancel := context.WithTimeout(ctx, client.queryTimeout)
	defer cancel()

	body, err := client.execute(queryCtx, sql)
	if err != nil {
		return err
	}

	var response jsonResponse[T]
	if err := json.Unmarshal(body, &response); err != nil {
		return fmt.Errorf("decode clickhouse response: %w", err)
	}

	*target = response.Data
	return nil
}

func (c *Client) execute(ctx context.Context, sql string) ([]byte, error) {
	endpoint, err := url.Parse(c.baseURL)
	if err != nil {
		return nil, fmt.Errorf("parse clickhouse url: %w", err)
	}

	values := endpoint.Query()
	values.Set("database", c.database)
	values.Set("default_format", "JSON")
	if c.user != "" {
		values.Set("user", c.user)
	}
	if c.password != "" {
		values.Set("password", c.password)
	}
	endpoint.RawQuery = values.Encode()

	request, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint.String(), bytes.NewBufferString(strings.TrimSpace(sql)))
	if err != nil {
		return nil, fmt.Errorf("create clickhouse request: %w", err)
	}
	request.Header.Set("Content-Type", "text/plain; charset=utf-8")

	response, err := c.httpClient.Do(request)
	if err != nil {
		return nil, fmt.Errorf("clickhouse request failed: %w", err)
	}
	defer response.Body.Close()

	body, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, fmt.Errorf("read clickhouse response: %w", err)
	}
	if response.StatusCode >= 400 {
		return nil, fmt.Errorf("clickhouse returned %s: %s", response.Status, strings.TrimSpace(string(body)))
	}

	return body, nil
}

func quoteString(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "\\'") + "'"
}

func quotedList(values []string) string {
	quoted := make([]string, 0, len(values))
	for _, value := range values {
		quoted = append(quoted, quoteString(value))
	}
	return strings.Join(quoted, ",")
}

func toUnixNS(value time.Time) uint64 {
	return uint64(value.UTC().UnixNano())
}

func intervalToNS(interval string) (uint64, error) {
	switch interval {
	case "1m":
		return uint64(time.Minute), nil
	case "5m":
		return uint64(5 * time.Minute), nil
	case "15m":
		return uint64(15 * time.Minute), nil
	case "1h":
		return uint64(time.Hour), nil
	case "1d":
		return uint64(24 * time.Hour), nil
	default:
		return 0, fmt.Errorf("unsupported interval %q", interval)
	}
}
