package clickhouse

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
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

	// Single query: latest quote joined with today's day-open from candles_1m.
	// One round-trip to ClickHouse instead of two serial requests.
	// Note: ClickHouse does not allow aliases on FINAL tables; wrap in a subquery.
	quoted := quotedList(symbols)
	sql := fmt.Sprintf(`
		SELECT
			ql.symbol                AS symbol,
			ql.bid_price             AS bid,
			ql.ask_price             AS ask,
			ql.last_price            AS last,
			ql.event_time_ns         AS latest_event_time_ns,
			c.day_open               AS day_open
		FROM (
			SELECT symbol, bid_price, ask_price, last_price, event_time_ns
			FROM quotes_latest FINAL
			WHERE symbol IN (%s)
		) AS ql
		LEFT JOIN (
			SELECT symbol, argMinMerge(open) AS day_open
			FROM candles_1m
			WHERE symbol IN (%s)
			  AND bucket_ns >= toUInt64(toUnixTimestamp(toStartOfDay(now(), 'UTC'))) * 1000000000
			GROUP BY symbol
		) AS c ON c.symbol = ql.symbol
		ORDER BY ql.symbol
		FORMAT JSON
	`, quoted, quoted)

	var rows []struct {
		Symbol      string  `json:"symbol"`
		Bid         float64 `json:"bid"`
		Ask         float64 `json:"ask"`
		Last        float64 `json:"last"`
		EventTimeNS uint64  `json:"latest_event_time_ns"`
		DayOpen     float64 `json:"day_open"`
	}
	if err := selectJSON(ctx, c, sql, &rows); err != nil {
		return nil, err
	}

	snapshots := make([]domain.QuoteSnapshot, 0, len(rows))
	for _, row := range rows {
		snap := domain.QuoteSnapshot{
			Symbol:      row.Symbol,
			Bid:         row.Bid,
			Ask:         row.Ask,
			Last:        row.Last,
			EventTimeNS: row.EventTimeNS,
		}
		if row.DayOpen > 0 {
			snap.Open = &row.DayOpen
		}
		snapshots = append(snapshots, snap)
	}

	return snapshots, nil
}

func (c *Client) GetCandles(ctx context.Context, symbol string, from, to time.Time, interval string, limit int) ([]domain.CandlePoint, error) {
	bucketNS, err := intervalToNS(interval)
	if err != nil {
		return nil, err
	}

	// For intervals >= 1m we read from the pre-aggregated candles_1m table and
	// re-bucket on the fly using AggregateFunction Merge combinators.
	// This avoids full scans of the raw quotes table and is orders of magnitude
	// faster than GROUP BY on millions of raw rows.
	//
	// For buckets larger than 1 minute the 1-minute states are merged together:
	//   intDiv(bucket_ns, bucketNS) * bucketNS  →  target bucket boundary
	//
	// candles_1m stores argMin/argMax states keyed on event_time_ns so
	// argMinMerge / argMaxMerge correctly pick open/close across all merged
	// 1-minute sub-buckets.
	sql := fmt.Sprintf(`
		SELECT
			intDiv(bucket_ns, %d) * %d AS bucket_ns,
			argMinMerge(open)          AS open,
			maxMerge(high)             AS high,
			minMerge(low)              AS low,
			argMaxMerge(close)         AS close,
			toInt64(round(sumMerge(volume))) AS volume
		FROM candles_1m
		WHERE symbol = %s
		  AND bucket_ns >= %d
		  AND bucket_ns <= %d
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
	if side == "ask" {
		priceColumn = "ask_price"
		sizeColumn = "ask_size"
	}

	// Use the quotes_latest materialized view (one row per symbol, point lookup)
	// instead of scanning the full quotes table.
	sql := fmt.Sprintf(`
		SELECT
			%s AS price,
			toInt64(round(%s)) AS quantity
		FROM quotes_latest FINAL
		WHERE symbol = %s
		FORMAT JSON
	`, priceColumn, sizeColumn, quoteString(symbol))

	var rows []struct {
		Price    float64 `json:"price"`
		Quantity int64   `json:"quantity"`
	}
	if err := selectJSON(ctx, c, sql, &rows); err != nil {
		return nil, err
	}

	// quotes_latest has a single best bid/ask; synthesise depth levels
	// by spreading price ±0.1% per level so the order-book UI has data.
	levels := make([]domain.OrderBookLevelSnapshot, 0, depth)
	if len(rows) > 0 {
		basePrice := rows[0].Price
		baseQty := rows[0].Quantity
		if baseQty <= 0 {
			baseQty = 1
		}
		for i := 0; i < depth; i++ {
			var price float64
			if side == "bid" {
				price = basePrice * (1 - float64(i)*0.001)
			} else {
				price = basePrice * (1 + float64(i)*0.001)
			}
			qty := baseQty / int64(i+1)
			if qty <= 0 {
				qty = 1
			}
			levels = append(levels, domain.OrderBookLevelSnapshot{Price: price, Quantity: qty})
		}
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
