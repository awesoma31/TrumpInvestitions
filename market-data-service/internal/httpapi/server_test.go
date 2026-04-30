package httpapi

import (
	"context"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/awesoma/trumpinvestitions/market-data-service/internal/config"
	"github.com/awesoma/trumpinvestitions/market-data-service/internal/domain"
	"github.com/awesoma/trumpinvestitions/market-data-service/internal/service"
)

type stubService struct{}

func (stubService) Health() domain.HealthResponse {
	return domain.HealthResponse{Status: "UP", Service: "market-data-service", Timestamp: time.Unix(0, 0).UTC().Format(time.RFC3339)}
}

func (stubService) Readiness(context.Context) (domain.ReadinessResponse, int) {
	return domain.ReadinessResponse{
		Status:  "READY",
		Service: "market-data-service",
		Dependencies: []domain.DependencyStatus{
			{Name: "clickhouse", Status: "UP"},
		},
		Timestamp: time.Unix(0, 0).UTC().Format(time.RFC3339),
	}, http.StatusOK
}

func (stubService) SearchInstruments(context.Context, string, int, int) (domain.InstrumentListResponse, error) {
	return domain.InstrumentListResponse{
		Items:  []domain.Instrument{domain.NewInstrument("AAPL")},
		Total:  1,
		Limit:  50,
		Offset: 0,
	}, nil
}

func (stubService) GetInstrumentBySymbol(context.Context, string) (domain.Instrument, error) {
	return domain.NewInstrument("AAPL"), nil
}

func (stubService) GetQuotes(context.Context, []string) (domain.QuoteListResponse, error) {
	return domain.QuoteListResponse{
		Items: []domain.Quote{
			domain.NewQuote(domain.QuoteSnapshot{
				Symbol:      "AAPL",
				Bid:         190.1,
				Ask:         190.2,
				Last:        190.15,
				EventTimeNS: 1_710_000_000_000_000_000,
			}),
		},
	}, nil
}

func (stubService) GetQuoteBySymbol(context.Context, string) (domain.Quote, error) {
	return domain.NewQuote(domain.QuoteSnapshot{
		Symbol:      "AAPL",
		Bid:         190.1,
		Ask:         190.2,
		Last:        190.15,
		EventTimeNS: 1_710_000_000_000_000_000,
	}), nil
}

func (stubService) GetCandleHistory(context.Context, string, time.Time, time.Time, string, int) (domain.CandleListResponse, error) {
	return domain.CandleListResponse{
		Symbol:   "AAPL",
		Interval: "1m",
		Items: []domain.Candle{
			domain.NewCandle(1_710_000_000_000_000_000, 1, 2, 1, 2, 100),
		},
	}, nil
}

func (stubService) GetOrderBook(context.Context, string, int) (domain.OrderBookResponse, error) {
	bestBid := "190.1"
	bestAsk := "190.2"
	spread := "0.1"
	return domain.OrderBookResponse{
		Symbol:    "AAPL",
		Bids:      []domain.OrderBookLevel{{Price: "190.1", Quantity: 10}},
		Asks:      []domain.OrderBookLevel{{Price: "190.2", Quantity: 15}},
		BestBid:   &bestBid,
		BestAsk:   &bestAsk,
		Spread:    &spread,
		Timestamp: time.Unix(0, 0).UTC().Format(time.RFC3339),
	}, nil
}

func TestHealthEndpoint(t *testing.T) {
	server := New(config.Config{BasePath: "/api/v1"}, stubService{}, log.New(io.Discard, "", 0))

	request := httptest.NewRequest(http.MethodGet, "/api/v1/system/health", nil)
	response := httptest.NewRecorder()
	server.Handler().ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", response.Code)
	}

	var payload domain.HealthResponse
	if err := json.Unmarshal(response.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if payload.Status != "UP" {
		t.Fatalf("expected UP, got %s", payload.Status)
	}
}

func TestQuotesEndpointValidation(t *testing.T) {
	server := New(config.Config{BasePath: "/api/v1"}, stubService{}, log.New(io.Discard, "", 0))

	request := httptest.NewRequest(http.MethodGet, "/api/v1/quotes", nil)
	response := httptest.NewRecorder()
	server.Handler().ServeHTTP(response, request)

	if response.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", response.Code)
	}
}

func TestOrderBookEndpoint(t *testing.T) {
	server := New(config.Config{BasePath: "/api/v1"}, stubService{}, log.New(io.Discard, "", 0))

	request := httptest.NewRequest(http.MethodGet, "/api/v1/order-book/AAPL?depth=5", nil)
	response := httptest.NewRecorder()
	server.Handler().ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", response.Code)
	}

	var payload domain.OrderBookResponse
	if err := json.Unmarshal(response.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if payload.Symbol != "AAPL" {
		t.Fatalf("expected AAPL, got %s", payload.Symbol)
	}
}

func TestMapsNotFound(t *testing.T) {
	notFoundService := stubNotFoundService{}
	server := New(config.Config{BasePath: "/api/v1"}, notFoundService, log.New(io.Discard, "", 0))

	request := httptest.NewRequest(http.MethodGet, "/api/v1/quotes/UNKNOWN", nil)
	response := httptest.NewRecorder()
	server.Handler().ServeHTTP(response, request)

	if response.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", response.Code)
	}
}

type stubNotFoundService struct{ stubService }

func (stubNotFoundService) GetQuoteBySymbol(context.Context, string) (domain.Quote, error) {
	return domain.Quote{}, service.ErrNotFound
}
