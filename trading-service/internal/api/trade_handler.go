package api

import (
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/vnikolaenko/trading-service/internal/app"
	"github.com/vnikolaenko/trading-service/internal/domain"
	"github.com/vnikolaenko/trading-service/internal/repository"
)

type TradeResponse struct {
	ID          string  `json:"id"`
	OrderID     string  `json:"orderId"`
	UserID      int64   `json:"userId"`
	Symbol      string  `json:"symbol"`
	Side        string  `json:"side"`
	Quantity    int     `json:"quantity"`
	Price       string  `json:"price"`
	GrossAmount string  `json:"grossAmount"`
	FeeAmount   *string `json:"feeAmount"`
	ExecutedAt  string  `json:"executedAt"`
	CreatedAt   string  `json:"createdAt"`
}

type TradeListResponse struct {
	Items  []TradeResponse `json:"items"`
	Total  int             `json:"total"`
	Limit  int             `json:"limit"`
	Offset int             `json:"offset"`
}

func getTradeHandler(svc *app.OrderService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tradeID := chi.URLParam(r, "tradeId")
		userID := strconv.FormatInt(getUserID(r), 10)
		trade, err := svc.GetTrade(r.Context(), tradeID, userID)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error(), newTraceID())
			return
		}
		if trade == nil {
			writeError(w, http.StatusNotFound, "NOT_FOUND", "trade not found", newTraceID())
			return
		}
		writeJSON(w, http.StatusOK, toTradeResponse(trade))
	}
}

func listTradesHandler(svc *app.OrderService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID := getUserID(r)
		q := r.URL.Query()
		filters := repository.TradeFilter{
			Limit:  parseLimit(q.Get("limit")),
			Offset: parseOffset(q.Get("offset")),
		}
		if sym := q.Get("symbol"); sym != "" {
			filters.Symbol = &sym
		}
		if side := q.Get("side"); side != "" {
			s := domain.OrderSide(side)
			filters.Side = &s
		}
		trades, total, err := svc.ListTrades(r.Context(), userID, filters)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error(), newTraceID())
			return
		}
		items := make([]TradeResponse, len(trades))
		for i, t := range trades {
			items[i] = toTradeResponse(&t)
		}
		writeJSON(w, http.StatusOK, TradeListResponse{
			Items:  items,
			Total:  total,
			Limit:  filters.Limit,
			Offset: filters.Offset,
		})
	}
}

func toTradeResponse(o *domain.OrderRecord) TradeResponse {
	resp := TradeResponse{
		ID:          o.TradeID.String(),
		OrderID:     o.ID.String(),
		UserID:      o.UserID,
		Symbol:      o.Symbol,
		Side:        string(o.Side),
		Quantity:    o.Quantity,
		Price:       *o.TradePrice,
		GrossAmount: *o.TradeGrossAmount,
		FeeAmount:   o.TradeFeeAmount,
		ExecutedAt:  o.TradeExecutedAt.Format(time.RFC3339),
		CreatedAt:   o.CreatedAt.Format(time.RFC3339),
	}
	return resp
}
