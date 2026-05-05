package httpapi

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/awesoma/trumpinvestitions/market-data-service/internal/config"
	"github.com/awesoma/trumpinvestitions/market-data-service/internal/domain"
	"github.com/awesoma/trumpinvestitions/market-data-service/internal/service"
)

type MarketDataService interface {
	Health() domain.HealthResponse
	Readiness(context.Context) (domain.ReadinessResponse, int)
	SearchInstruments(context.Context, string, int, int) (domain.InstrumentListResponse, error)
	GetInstrumentBySymbol(context.Context, string) (domain.Instrument, error)
	GetQuotes(context.Context, []string) (domain.QuoteListResponse, error)
	GetQuoteBySymbol(context.Context, string) (domain.Quote, error)
	GetCandleHistory(context.Context, string, time.Time, time.Time, string, int) (domain.CandleListResponse, error)
	GetOrderBook(context.Context, string, int) (domain.OrderBookResponse, error)
}

type Server struct {
	cfg     config.Config
	service MarketDataService
	logger  *log.Logger
	router  http.Handler
}

func New(cfg config.Config, service MarketDataService, logger *log.Logger) *Server {
	s := &Server{
		cfg:     cfg,
		service: service,
		logger:  logger,
	}
	s.router = s.routes()
	return s
}

func (s *Server) Handler() http.Handler {
	return s.router
}

func (s *Server) routes() http.Handler {
	mux := http.NewServeMux()
	base := s.cfg.BasePath

	mux.HandleFunc("GET "+base+"/system/health", s.handleHealth)
	mux.HandleFunc("GET "+base+"/system/ready", s.handleReady)
	mux.HandleFunc("GET "+base+"/instruments", s.handleSearchInstruments)
	mux.HandleFunc("GET "+base+"/instruments/{symbol}", s.handleGetInstrument)
	mux.HandleFunc("GET "+base+"/quotes", s.handleGetQuotes)
	mux.HandleFunc("GET "+base+"/quotes/{symbol}", s.handleGetQuote)
	mux.HandleFunc("GET "+base+"/history/candles", s.handleGetCandles)
	mux.HandleFunc("GET "+base+"/order-book/{symbol}", s.handleGetOrderBook)

	return s.withMiddleware(mux)
}

func (s *Server) withMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		traceID := requestTraceID(r)
		w.Header().Set("X-Trace-Id", traceID)
		w.Header().Set("Content-Type", "application/json")

		start := time.Now()
		ctx := context.WithValue(r.Context(), traceIDKey{}, traceID)
		next.ServeHTTP(w, r.WithContext(ctx))
		s.logger.Printf("%s %s %s trace_id=%s duration=%s", r.Method, r.URL.Path, r.RemoteAddr, traceID, time.Since(start).Truncate(time.Millisecond))
	})
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, s.service.Health())
}

func (s *Server) handleReady(w http.ResponseWriter, r *http.Request) {
	response, statusCode := s.service.Readiness(r.Context())
	writeJSON(w, statusCode, response)
}

func (s *Server) handleSearchInstruments(w http.ResponseWriter, r *http.Request) {
	traceID := traceIDFromContext(r.Context())

	limit, offset, ok := s.parsePagination(w, r, traceID)
	if !ok {
		return
	}

	response, err := s.service.SearchInstruments(r.Context(), r.URL.Query().Get("q"), limit, offset)
	if err != nil {
		s.writeInternalError(w, traceID, err)
		return
	}

	writeJSON(w, http.StatusOK, response)
}

func (s *Server) handleGetInstrument(w http.ResponseWriter, r *http.Request) {
	traceID := traceIDFromContext(r.Context())

	response, err := s.service.GetInstrumentBySymbol(r.Context(), r.PathValue("symbol"))
	if err != nil {
		s.writeError(w, traceID, err, "")
		return
	}

	writeJSON(w, http.StatusOK, response)
}

func (s *Server) handleGetQuotes(w http.ResponseWriter, r *http.Request) {
	traceID := traceIDFromContext(r.Context())
	rawSymbols := strings.Split(r.URL.Query().Get("symbols"), ",")
	symbols := make([]string, 0, len(rawSymbols))
	for _, symbol := range rawSymbols {
		if trimmed := strings.TrimSpace(symbol); trimmed != "" {
			symbols = append(symbols, trimmed)
		}
	}
	if len(symbols) == 0 {
		writeError(w, http.StatusBadRequest, domain.ErrorResponse{
			Code:    "VALIDATION_ERROR",
			Message: "symbols is required",
			Details: []domain.ErrorDetail{{Field: "symbols", Issue: "expected comma-separated ticker list"}},
			TraceID: traceID,
		})
		return
	}

	response, err := s.service.GetQuotes(r.Context(), symbols)
	if err != nil {
		s.writeInternalError(w, traceID, err)
		return
	}

	writeJSON(w, http.StatusOK, response)
}

func (s *Server) handleGetQuote(w http.ResponseWriter, r *http.Request) {
	traceID := traceIDFromContext(r.Context())

	response, err := s.service.GetQuoteBySymbol(r.Context(), r.PathValue("symbol"))
	if err != nil {
		s.writeError(w, traceID, err, "")
		return
	}

	writeJSON(w, http.StatusOK, response)
}

func (s *Server) handleGetCandles(w http.ResponseWriter, r *http.Request) {
	traceID := traceIDFromContext(r.Context())
	query := r.URL.Query()

	symbol := query.Get("symbol")
	if strings.TrimSpace(symbol) == "" {
		writeError(w, http.StatusBadRequest, domain.ErrorResponse{
			Code:    "VALIDATION_ERROR",
			Message: "symbol is required",
			Details: []domain.ErrorDetail{{Field: "symbol", Issue: "query parameter is required"}},
			TraceID: traceID,
		})
		return
	}

	from, err := time.Parse(time.RFC3339, query.Get("from"))
	if err != nil {
		writeError(w, http.StatusBadRequest, domain.ErrorResponse{
			Code:    "VALIDATION_ERROR",
			Message: "from must be RFC3339 timestamp",
			Details: []domain.ErrorDetail{{Field: "from", Issue: err.Error()}},
			TraceID: traceID,
		})
		return
	}

	to, err := time.Parse(time.RFC3339, query.Get("to"))
	if err != nil {
		writeError(w, http.StatusBadRequest, domain.ErrorResponse{
			Code:    "VALIDATION_ERROR",
			Message: "to must be RFC3339 timestamp",
			Details: []domain.ErrorDetail{{Field: "to", Issue: err.Error()}},
			TraceID: traceID,
		})
		return
	}
	if !from.Before(to) {
		writeError(w, http.StatusBadRequest, domain.ErrorResponse{
			Code:    "VALIDATION_ERROR",
			Message: "from must be earlier than to",
			Details: []domain.ErrorDetail{{Field: "from", Issue: "must be earlier than to"}},
			TraceID: traceID,
		})
		return
	}

	interval := query.Get("interval")
	if err := service.ValidateInterval(interval); err != nil {
		writeError(w, http.StatusBadRequest, domain.ErrorResponse{
			Code:    "VALIDATION_ERROR",
			Message: "interval is invalid",
			Details: []domain.ErrorDetail{{Field: "interval", Issue: err.Error()}},
			TraceID: traceID,
		})
		return
	}

	limit := 1000
	if rawLimit := strings.TrimSpace(query.Get("limit")); rawLimit != "" {
		parsed, err := strconv.Atoi(rawLimit)
		if err != nil || parsed < 1 || parsed > 10000 {
			writeError(w, http.StatusBadRequest, domain.ErrorResponse{
				Code:    "VALIDATION_ERROR",
				Message: "limit must be between 1 and 10000",
				Details: []domain.ErrorDetail{{Field: "limit", Issue: "expected integer in range [1, 10000]"}},
				TraceID: traceID,
			})
			return
		}
		limit = parsed
	}

	response, err := s.service.GetCandleHistory(r.Context(), symbol, from, to, interval, limit)
	if err != nil {
		s.writeError(w, traceID, err, "")
		return
	}

	writeJSON(w, http.StatusOK, response)
}

func (s *Server) handleGetOrderBook(w http.ResponseWriter, r *http.Request) {
	traceID := traceIDFromContext(r.Context())

	depth := 20
	if rawDepth := strings.TrimSpace(r.URL.Query().Get("depth")); rawDepth != "" {
		parsed, err := strconv.Atoi(rawDepth)
		if err != nil || parsed < 1 || parsed > 100 {
			writeError(w, http.StatusBadRequest, domain.ErrorResponse{
				Code:    "VALIDATION_ERROR",
				Message: "depth must be between 1 and 100",
				Details: []domain.ErrorDetail{{Field: "depth", Issue: "expected integer in range [1, 100]"}},
				TraceID: traceID,
			})
			return
		}
		depth = parsed
	}

	response, err := s.service.GetOrderBook(r.Context(), r.PathValue("symbol"), depth)
	if err != nil {
		s.writeError(w, traceID, err, "")
		return
	}

	writeJSON(w, http.StatusOK, response)
}

func (s *Server) parsePagination(w http.ResponseWriter, r *http.Request, traceID string) (int, int, bool) {
	limit := 50
	offset := 0

	if rawLimit := strings.TrimSpace(r.URL.Query().Get("limit")); rawLimit != "" {
		parsed, err := strconv.Atoi(rawLimit)
		if err != nil || parsed < 1 || parsed > 200 {
			writeError(w, http.StatusBadRequest, domain.ErrorResponse{
				Code:    "VALIDATION_ERROR",
				Message: "limit must be between 1 and 200",
				Details: []domain.ErrorDetail{{Field: "limit", Issue: "expected integer in range [1, 200]"}},
				TraceID: traceID,
			})
			return 0, 0, false
		}
		limit = parsed
	}

	if rawOffset := strings.TrimSpace(r.URL.Query().Get("offset")); rawOffset != "" {
		parsed, err := strconv.Atoi(rawOffset)
		if err != nil || parsed < 0 {
			writeError(w, http.StatusBadRequest, domain.ErrorResponse{
				Code:    "VALIDATION_ERROR",
				Message: "offset must be zero or greater",
				Details: []domain.ErrorDetail{{Field: "offset", Issue: "expected integer greater than or equal to zero"}},
				TraceID: traceID,
			})
			return 0, 0, false
		}
		offset = parsed
	}

	return limit, offset, true
}

func (s *Server) writeError(w http.ResponseWriter, traceID string, err error, field string) {
	switch {
	case errors.Is(err, service.ErrNotFound):
		writeError(w, http.StatusNotFound, domain.ErrorResponse{
			Code:    "NOT_FOUND",
			Message: "requested resource was not found",
			TraceID: traceID,
		})
	default:
		if field != "" {
			writeError(w, http.StatusBadRequest, domain.ErrorResponse{
				Code:    "VALIDATION_ERROR",
				Message: fmt.Sprintf("%s is invalid", field),
				Details: []domain.ErrorDetail{{Field: field, Issue: err.Error()}},
				TraceID: traceID,
			})
			return
		}
		s.writeInternalError(w, traceID, err)
	}
}

func (s *Server) writeInternalError(w http.ResponseWriter, traceID string, err error) {
	s.logger.Printf("internal error trace_id=%s err=%v", traceID, err)
	writeError(w, http.StatusInternalServerError, domain.ErrorResponse{
		Code:    "INTERNAL_ERROR",
		Message: "internal server error",
		TraceID: traceID,
	})
}

func writeJSON(w http.ResponseWriter, statusCode int, payload any) {
	w.WriteHeader(statusCode)
	if err := json.NewEncoder(w).Encode(payload); err != nil {
		http.Error(w, `{"code":"INTERNAL_ERROR","message":"encode response","traceId":"unknown"}`, http.StatusInternalServerError)
	}
}

func writeError(w http.ResponseWriter, statusCode int, response domain.ErrorResponse) {
	writeJSON(w, statusCode, response)
}

type traceIDKey struct{}

func traceIDFromContext(ctx context.Context) string {
	if value, ok := ctx.Value(traceIDKey{}).(string); ok {
		return value
	}
	return "unknown"
}

func requestTraceID(r *http.Request) string {
	if existing := strings.TrimSpace(r.Header.Get("X-Trace-Id")); existing != "" {
		return existing
	}

	var data [16]byte
	if _, err := rand.Read(data[:]); err != nil {
		return fmt.Sprintf("trace-%d", time.Now().UnixNano())
	}
	return hex.EncodeToString(data[:])
}
