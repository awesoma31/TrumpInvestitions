package service

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"strings"
	"time"

	"github.com/awesoma/trumpinvestitions/market-data-service/internal/domain"
)

var ErrNotFound = errors.New("resource not found")

type Repository interface {
	Ping(context.Context) error
	ListSymbols(context.Context, string, int, int) ([]string, int, error)
	SymbolExists(context.Context, string) (bool, error)
	GetLatestQuotes(context.Context, []string) ([]domain.QuoteSnapshot, error)
	GetCandles(context.Context, string, time.Time, time.Time, string, int) ([]domain.CandlePoint, error)
	GetOrderBook(context.Context, string, int) ([]domain.OrderBookLevelSnapshot, []domain.OrderBookLevelSnapshot, error)
}

type MarketDataService struct {
	repo Repository
}

func New(repo Repository) *MarketDataService {
	return &MarketDataService{repo: repo}
}

func (s *MarketDataService) Health() domain.HealthResponse {
	return domain.HealthResponse{
		Status:    "UP",
		Service:   "market-data-service",
		Timestamp: time.Now().UTC().Format(time.RFC3339Nano),
	}
}

func (s *MarketDataService) Readiness(ctx context.Context) (domain.ReadinessResponse, int) {
	status := "READY"
	dependencyStatus := "UP"
	code := 200

	if err := s.repo.Ping(ctx); err != nil {
		status = "NOT_READY"
		dependencyStatus = "DOWN"
		code = 503
	}

	return domain.ReadinessResponse{
		Status:  status,
		Service: "market-data-service",
		Dependencies: []domain.DependencyStatus{
			{Name: "clickhouse", Status: dependencyStatus},
		},
		Timestamp: time.Now().UTC().Format(time.RFC3339Nano),
	}, code
}

func (s *MarketDataService) SearchInstruments(ctx context.Context, query string, limit, offset int) (domain.InstrumentListResponse, error) {
	symbols, total, err := s.repo.ListSymbols(ctx, normalizeSymbolQuery(query), limit, offset)
	if err != nil {
		return domain.InstrumentListResponse{}, err
	}

	items := make([]domain.Instrument, 0, len(symbols))
	for _, symbol := range symbols {
		items = append(items, domain.NewInstrument(symbol))
	}

	return domain.InstrumentListResponse{
		Items:  items,
		Total:  total,
		Limit:  limit,
		Offset: offset,
	}, nil
}

func (s *MarketDataService) GetInstrumentBySymbol(ctx context.Context, symbol string) (domain.Instrument, error) {
	symbol = normalizeSymbol(symbol)

	exists, err := s.repo.SymbolExists(ctx, symbol)
	if err != nil {
		return domain.Instrument{}, err
	}
	if !exists {
		return domain.Instrument{}, ErrNotFound
	}

	return domain.NewInstrument(symbol), nil
}

func (s *MarketDataService) GetQuotes(ctx context.Context, symbols []string) (domain.QuoteListResponse, error) {
	normalized := normalizeSymbols(symbols)
	if len(normalized) == 0 {
		return domain.QuoteListResponse{Items: []domain.Quote{}}, nil
	}

	snapshots, err := s.repo.GetLatestQuotes(ctx, normalized)
	if err != nil {
		return domain.QuoteListResponse{}, err
	}

	items := make([]domain.Quote, 0, len(snapshots))
	for _, snapshot := range snapshots {
		items = append(items, domain.NewQuote(snapshot))
	}

	return domain.QuoteListResponse{Items: items}, nil
}

func (s *MarketDataService) GetQuoteBySymbol(ctx context.Context, symbol string) (domain.Quote, error) {
	snapshots, err := s.repo.GetLatestQuotes(ctx, []string{normalizeSymbol(symbol)})
	if err != nil {
		return domain.Quote{}, err
	}
	if len(snapshots) == 0 {
		return domain.Quote{}, ErrNotFound
	}

	return domain.NewQuote(snapshots[0]), nil
}

func (s *MarketDataService) GetCandleHistory(ctx context.Context, symbol string, from, to time.Time, interval string, limit int) (domain.CandleListResponse, error) {
	symbol = normalizeSymbol(symbol)

	points, err := s.repo.GetCandles(ctx, symbol, from.UTC(), to.UTC(), interval, limit)
	if err != nil {
		return domain.CandleListResponse{}, err
	}

	items := make([]domain.Candle, 0, len(points))
	for _, point := range points {
		items = append(items, domain.NewCandle(point.TimestampNS, point.Open, point.High, point.Low, point.Close, point.Volume))
	}

	return domain.CandleListResponse{
		Symbol:   symbol,
		Interval: interval,
		Items:    items,
	}, nil
}

func (s *MarketDataService) GetOrderBook(ctx context.Context, symbol string, depth int) (domain.OrderBookResponse, error) {
	symbol = normalizeSymbol(symbol)

	snapshots, err := s.repo.GetLatestQuotes(ctx, []string{symbol})
	if err != nil {
		return domain.OrderBookResponse{}, err
	}
	if len(snapshots) == 0 {
		return domain.OrderBookResponse{}, ErrNotFound
	}

	bids, asks, err := s.repo.GetOrderBook(ctx, symbol, depth)
	if err != nil {
		return domain.OrderBookResponse{}, err
	}

	response := domain.OrderBookResponse{
		Symbol:    symbol,
		Bids:      make([]domain.OrderBookLevel, 0, len(bids)),
		Asks:      make([]domain.OrderBookLevel, 0, len(asks)),
		Timestamp: time.Unix(int64(snapshots[0].EventTimeNS/1_000_000_000), int64(snapshots[0].EventTimeNS%1_000_000_000)).UTC().Format(time.RFC3339Nano),
	}

	for _, bid := range bids {
		response.Bids = append(response.Bids, domain.NewOrderBookLevel(bid.Price, bid.Quantity))
	}
	for _, ask := range asks {
		response.Asks = append(response.Asks, domain.NewOrderBookLevel(ask.Price, ask.Quantity))
	}

	if len(response.Bids) > 0 {
		value := response.Bids[0].Price
		response.BestBid = &value
	}
	if len(response.Asks) > 0 {
		value := response.Asks[0].Price
		response.BestAsk = &value
	}
	if response.BestBid != nil && response.BestAsk != nil {
		response.Spread = domain.FormatSpread(snapshots[0].Bid, snapshots[0].Ask)
	}

	return response, nil
}

func normalizeSymbols(symbols []string) []string {
	result := make([]string, 0, len(symbols))
	seen := make(map[string]struct{}, len(symbols))

	for _, symbol := range symbols {
		normalized := normalizeSymbol(symbol)
		if normalized == "" {
			continue
		}
		if _, ok := seen[normalized]; ok {
			continue
		}
		seen[normalized] = struct{}{}
		result = append(result, normalized)
	}

	slices.Sort(result)
	return result
}

func normalizeSymbol(symbol string) string {
	return strings.ToUpper(strings.TrimSpace(symbol))
}

func normalizeSymbolQuery(query string) string {
	return strings.ToUpper(strings.TrimSpace(query))
}

func ValidateInterval(interval string) error {
	switch interval {
	case "1m", "5m", "15m", "1h", "1d":
		return nil
	default:
		return fmt.Errorf("unsupported interval %q", interval)
	}
}
