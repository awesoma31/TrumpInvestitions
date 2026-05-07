package service

import (
	"context"
	"errors"
	"fmt"
	"math/rand"
	"slices"
	"strings"
	"sync"
	"time"

	"github.com/awesoma/trumpinvestitions/market-data-service/internal/domain"
	"github.com/awesoma/trumpinvestitions/market-data-service/internal/telemetry"
	"go.opentelemetry.io/otel/attribute"
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

type cacheEntry[T any] struct {
	data      T
	expiresAt time.Time
}

type MarketDataService struct {
	repo    Repository
	mu      sync.RWMutex

	quoteCache     map[string]*cacheEntry[[]domain.QuoteSnapshot]
	orderBookCache map[string]*cacheEntry[[2][]domain.OrderBookLevelSnapshot]
	candleCache    map[string]*cacheEntry[[]domain.CandlePoint]
	symbolCache    *cacheEntry[[]string]
	symbolCountCache map[string]*cacheEntry[int]

	quoteTTL     time.Duration
	orderBookTTL time.Duration
	candleTTL    time.Duration
	symbolTTL    time.Duration
}

func New(repo Repository) *MarketDataService {
	return &MarketDataService{
		repo:             repo,
		quoteCache:       make(map[string]*cacheEntry[[]domain.QuoteSnapshot]),
		orderBookCache:   make(map[string]*cacheEntry[[2][]domain.OrderBookLevelSnapshot]),
		candleCache:      make(map[string]*cacheEntry[[]domain.CandlePoint]),
		symbolCountCache: make(map[string]*cacheEntry[int]),
		quoteTTL:         200 * time.Millisecond,
		orderBookTTL:     5 * time.Second,
		candleTTL:        30 * time.Second,
		symbolTTL:        30 * time.Second,
	}
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
	q := normalizeSymbolQuery(query)
	cacheKey := fmt.Sprintf("%s:%d:%d", q, limit, offset)
	now := time.Now()

	// symbols list
	var symbols []string
	s.mu.RLock()
	if s.symbolCache != nil && now.Before(s.symbolCache.expiresAt) {
		symbols = s.symbolCache.data
	}
	s.mu.RUnlock()

	// count
	var total int
	s.mu.RLock()
	if e, ok := s.symbolCountCache[cacheKey]; ok && now.Before(e.expiresAt) {
		total = e.data
		s.mu.RUnlock()
		if symbols != nil {
			start := offset
			if start > len(symbols) {
				start = len(symbols)
			}
			end := start + limit
			if end > len(symbols) {
				end = len(symbols)
			}
			page := symbols[start:end]
			items := make([]domain.Instrument, 0, len(page))
			for _, sym := range page {
				items = append(items, domain.NewInstrument(sym))
			}
			return domain.InstrumentListResponse{Items: items, Total: total, Limit: limit, Offset: offset}, nil
		}
	} else {
		s.mu.RUnlock()
	}

	syms, tot, err := s.repo.ListSymbols(ctx, q, limit, offset)
	if err != nil {
		// serve stale data if both caches are available
		s.mu.RLock()
		staleCount, hasCount := s.symbolCountCache[cacheKey]
		staleSyms := s.symbolCache
		s.mu.RUnlock()
		if hasCount && staleSyms != nil {
			total := staleCount.data
			start := offset
			if start > len(staleSyms.data) {
				start = len(staleSyms.data)
			}
			end := start + limit
			if end > len(staleSyms.data) {
				end = len(staleSyms.data)
			}
			page := staleSyms.data[start:end]
			items := make([]domain.Instrument, 0, len(page))
			for _, sym := range page {
				items = append(items, domain.NewInstrument(sym))
			}
			return domain.InstrumentListResponse{Items: items, Total: total, Limit: limit, Offset: offset}, nil
		}
		return domain.InstrumentListResponse{}, err
	}

	s.mu.Lock()
	s.symbolCountCache[cacheKey] = &cacheEntry[int]{data: tot, expiresAt: now.Add(s.symbolTTL)}
	// Only cache the full unfiltered first page — partial/paginated/filtered
	// results would cause false 404s in GetInstrumentBySymbol.
	if q == "" && offset == 0 {
		if s.symbolCache == nil || now.After(s.symbolCache.expiresAt) {
			s.symbolCache = &cacheEntry[[]string]{data: syms, expiresAt: now.Add(s.symbolTTL)}
		}
	}
	s.mu.Unlock()

	items := make([]domain.Instrument, 0, len(syms))
	for _, sym := range syms {
		items = append(items, domain.NewInstrument(sym))
	}
	return domain.InstrumentListResponse{Items: items, Total: tot, Limit: limit, Offset: offset}, nil
}

func (s *MarketDataService) GetInstrumentBySymbol(ctx context.Context, symbol string) (domain.Instrument, error) {
	symbol = normalizeSymbol(symbol)

	// check symbol cache first
	now := time.Now()
	s.mu.RLock()
	if s.symbolCache != nil && now.Before(s.symbolCache.expiresAt) {
		for _, sym := range s.symbolCache.data {
			if sym == symbol {
				s.mu.RUnlock()
				return domain.NewInstrument(symbol), nil
			}
		}
		// Symbol not in cached page — cache may be partial (paginated),
		// so fall through to DB lookup instead of returning 404.
	}
	s.mu.RUnlock()

	exists, err := s.repo.SymbolExists(ctx, symbol)
	if err != nil {
		return domain.Instrument{}, err
	}
	if !exists {
		return domain.Instrument{}, ErrNotFound
	}
	return domain.NewInstrument(symbol), nil
}

func (s *MarketDataService) getLatestQuotesCached(ctx context.Context, symbols []string) ([]domain.QuoteSnapshot, error) {
	key := strings.Join(symbols, ",")
	now := time.Now()

	s.mu.RLock()
	if e, ok := s.quoteCache[key]; ok && now.Before(e.expiresAt) {
		v := e.data
		s.mu.RUnlock()
		return v, nil
	}
	s.mu.RUnlock()

	snapshots, err := s.repo.GetLatestQuotes(ctx, symbols)
	if err != nil {
		// serve stale data if available
		s.mu.RLock()
		if stale, ok := s.quoteCache[key]; ok {
			v := stale.data
			s.mu.RUnlock()
			return v, nil
		}
		s.mu.RUnlock()
		return nil, err
	}

	s.mu.Lock()
	s.quoteCache[key] = &cacheEntry[[]domain.QuoteSnapshot]{data: snapshots, expiresAt: now.Add(s.quoteTTL)}
	s.mu.Unlock()
	return snapshots, nil
}

func (s *MarketDataService) GetQuotes(ctx context.Context, symbols []string) (domain.QuoteListResponse, error) {
	ctx, span := telemetry.Tracer("market-data-service").Start(ctx, "MarketDataService.GetQuotes")
	defer span.End()
	span.SetAttributes(attribute.StringSlice("symbols", symbols))

	normalized := normalizeSymbols(symbols)
	if len(normalized) == 0 {
		return domain.QuoteListResponse{Items: []domain.Quote{}}, nil
	}

	snapshots, err := s.getLatestQuotesCached(ctx, normalized)
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
	ctx, span := telemetry.Tracer("market-data-service").Start(ctx, "MarketDataService.GetQuoteBySymbol")
	defer span.End()
	span.SetAttributes(attribute.String("symbol", symbol))

	snapshots, err := s.getLatestQuotesCached(ctx, []string{normalizeSymbol(symbol)})
	if err != nil {
		return domain.Quote{}, err
	}
	if len(snapshots) == 0 {
		return domain.Quote{}, ErrNotFound
	}
	return domain.NewQuote(snapshots[0]), nil
}

func (s *MarketDataService) GetCandleHistory(ctx context.Context, symbol string, from, to time.Time, interval string, limit int) (domain.CandleListResponse, error) {
	ctx, span := telemetry.Tracer("market-data-service").Start(ctx, "MarketDataService.GetCandleHistory")
	defer span.End()
	span.SetAttributes(attribute.String("symbol", symbol), attribute.String("interval", interval))

	symbol = normalizeSymbol(symbol)
	// Round `to` down to the candleTTL bucket so requests within the same
	// window share a cache entry (otherwise time.Now() makes every key unique).
	roundedTo := to.Truncate(s.candleTTL)
	key := fmt.Sprintf("%s:%d:%d:%s:%d", symbol, from.Unix(), roundedTo.Unix(), interval, limit)
	now := time.Now()

	s.mu.RLock()
	if e, ok := s.candleCache[key]; ok && now.Before(e.expiresAt) {
		points := e.data
		s.mu.RUnlock()
		return s.buildCandleResponse(symbol, interval, points), nil
	}
	s.mu.RUnlock()

	points, err := s.repo.GetCandles(ctx, symbol, from.UTC(), to.UTC(), interval, limit)
	if err != nil {
		// serve stale data if available
		s.mu.RLock()
		if stale, ok := s.candleCache[key]; ok {
			pts := stale.data
			s.mu.RUnlock()
			return s.buildCandleResponse(symbol, interval, pts), nil
		}
		s.mu.RUnlock()
		return domain.CandleListResponse{}, err
	}

	s.mu.Lock()
	s.candleCache[key] = &cacheEntry[[]domain.CandlePoint]{data: points, expiresAt: now.Add(jitter(s.candleTTL, 0.3))}
	s.mu.Unlock()

	return s.buildCandleResponse(symbol, interval, points), nil
}

func (s *MarketDataService) buildCandleResponse(symbol, interval string, points []domain.CandlePoint) domain.CandleListResponse {
	items := make([]domain.Candle, 0, len(points))
	for _, point := range points {
		items = append(items, domain.NewCandle(point.TimestampNS, point.Open, point.High, point.Low, point.Close, point.Volume))
	}
	return domain.CandleListResponse{Symbol: symbol, Interval: interval, Items: items}
}

func (s *MarketDataService) GetOrderBook(ctx context.Context, symbol string, depth int) (domain.OrderBookResponse, error) {
	ctx, span := telemetry.Tracer("market-data-service").Start(ctx, "MarketDataService.GetOrderBook")
	defer span.End()
	span.SetAttributes(attribute.String("symbol", symbol), attribute.Int("depth", depth))

	symbol = normalizeSymbol(symbol)
	now := time.Now()

	s.mu.RLock()
	if e, ok := s.orderBookCache[symbol]; ok && now.Before(e.expiresAt) {
		sides := e.data
		s.mu.RUnlock()
		return s.buildOrderBookResponse(symbol, sides[0], sides[1]), nil
	}
	s.mu.RUnlock()

	snapshots, err := s.getLatestQuotesCached(ctx, []string{symbol})
	if err != nil {
		return domain.OrderBookResponse{}, err
	}
	if len(snapshots) == 0 {
		return domain.OrderBookResponse{}, ErrNotFound
	}

	bids, asks, err := s.repo.GetOrderBook(ctx, symbol, depth)
	if err != nil {
		// serve stale data if available
		s.mu.RLock()
		if stale, ok := s.orderBookCache[symbol]; ok {
			sides := stale.data
			s.mu.RUnlock()
			return s.buildOrderBookResponse(symbol, sides[0], sides[1]), nil
		}
		s.mu.RUnlock()
		return domain.OrderBookResponse{}, err
	}

	s.mu.Lock()
	s.orderBookCache[symbol] = &cacheEntry[[2][]domain.OrderBookLevelSnapshot]{
		data:      [2][]domain.OrderBookLevelSnapshot{bids, asks},
		expiresAt: now.Add(jitter(s.orderBookTTL, 0.3)),
	}
	s.mu.Unlock()

	return s.buildOrderBookResponse(symbol, bids, asks), nil
}

func (s *MarketDataService) buildOrderBookResponse(symbol string, bids, asks []domain.OrderBookLevelSnapshot) domain.OrderBookResponse {
	response := domain.OrderBookResponse{
		Symbol:    symbol,
		Bids:      make([]domain.OrderBookLevel, 0, len(bids)),
		Asks:      make([]domain.OrderBookLevel, 0, len(asks)),
		Timestamp: time.Now().UTC().Format(time.RFC3339Nano),
	}
	for _, bid := range bids {
		response.Bids = append(response.Bids, domain.NewOrderBookLevel(bid.Price, bid.Quantity))
	}
	for _, ask := range asks {
		response.Asks = append(response.Asks, domain.NewOrderBookLevel(ask.Price, ask.Quantity))
	}
	if len(response.Bids) > 0 {
		v := response.Bids[0].Price
		response.BestBid = &v
	}
	if len(response.Asks) > 0 {
		v := response.Asks[0].Price
		response.BestAsk = &v
	}
	if len(bids) > 0 && len(asks) > 0 {
		response.Spread = domain.FormatSpread(bids[0].Price, asks[0].Price)
	}
	return response
}

// jitter adds ±fraction random spread to d to prevent cache stampedes.
func jitter(d time.Duration, fraction float64) time.Duration {
	delta := float64(d) * fraction
	return d + time.Duration((rand.Float64()*2-1)*delta)
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
