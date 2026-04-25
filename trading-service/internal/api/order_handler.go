package api

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/vnikolaenko/trading-service/internal/app"
	"github.com/vnikolaenko/trading-service/internal/domain"
	"github.com/vnikolaenko/trading-service/internal/repository"
)

type CreateOrderRequest struct {
	Symbol   string `json:"symbol"`
	Side     string `json:"side"`
	Type     string `json:"type"`
	Quantity int    `json:"quantity"`
}

func createOrderHandler(svc *app.OrderService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req CreateOrderRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "invalid request body", newTraceID())
			return
		}
		userID := getUserID(r)
		order, err := svc.CreateOrder(r.Context(), app.CreateOrderReq{
			UserID:   userID,
			Symbol:   req.Symbol,
			Side:     domain.OrderSide(req.Side),
			Type:     domain.OrderType(req.Type),
			Quantity: req.Quantity,
		})
		if err != nil {
			switch err {
			case app.ErrInsufficientFunds:
				writeError(w, http.StatusUnprocessableEntity, "INSUFFICIENT_FUNDS", "Not enough cash balance to place BUY market order", newTraceID())
				return
			case app.ErrInsufficientAssets:
				writeError(w, http.StatusUnprocessableEntity, "INSUFFICIENT_ASSETS", "Not enough asset quantity to place SELL market order", newTraceID())
				return
			case app.ErrInsufficientMarketVolume:
				writeError(w, http.StatusUnprocessableEntity, "INSUFFICIENT_MARKET_VOLUME", "Not enough market volume to execute order", newTraceID())
				return
			default:
				writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error(), newTraceID())
				return
			}
		}
		writeJSON(w, http.StatusCreated, toOrderResponse(order))
	}
}

func getOrderHandler(svc *app.OrderService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		orderID := chi.URLParam(r, "orderId")
		userID := strconv.FormatInt(getUserID(r), 10)
		order, err := svc.GetOrder(r.Context(), orderID, userID)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error(), newTraceID())
			return
		}
		if order == nil {
			writeError(w, http.StatusNotFound, "NOT_FOUND", "order not found", newTraceID())
			return
		}
		writeJSON(w, http.StatusOK, toOrderResponse(order))
	}
}

func listOrdersHandler(svc *app.OrderService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID := getUserID(r)
		q := r.URL.Query()
		filters := repository.OrderFilter{
			Limit:  parseLimit(q.Get("limit")),
			Offset: parseOffset(q.Get("offset")),
		}
		if s := q.Get("status"); s != "" {
			status := domain.OrderStatus(s)
			filters.Status = &status
		}
		if sym := q.Get("symbol"); sym != "" {
			filters.Symbol = &sym
		}
		if side := q.Get("side"); side != "" {
			s := domain.OrderSide(side)
			filters.Side = &s
		}
		orders, total, err := svc.ListOrders(r.Context(), userID, filters)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error(), newTraceID())
			return
		}
		items := make([]OrderResponse, len(orders))
		for i, o := range orders {
			items[i] = toOrderResponse(&o)
		}
		writeJSON(w, http.StatusOK, OrderListResponse{
			Items:  items,
			Total:  total,
			Limit:  filters.Limit,
			Offset: filters.Offset,
		})
	}
}

func cancelOrderHandler(svc *app.OrderService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		orderID := chi.URLParam(r, "orderId")
		userID := strconv.FormatInt(getUserID(r), 10)
		order, err := svc.CancelOrder(r.Context(), orderID, userID)
		if err != nil {
			switch err {
			case app.ErrOrderNotFound:
				writeError(w, http.StatusNotFound, "NOT_FOUND", "order not found", newTraceID())
			case app.ErrOrderNotCancellable:
				writeError(w, http.StatusConflict, "CONFLICT", "order cannot be cancelled in current state", newTraceID())
			default:
				writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error(), newTraceID())
			}
			return
		}
		writeJSON(w, http.StatusOK, toOrderResponse(order))
	}
}

func parseLimit(s string) int {
	limit, err := strconv.Atoi(s)
	if err != nil || limit < 1 {
		return 50
	}
	if limit > 200 {
		limit = 200
	}
	return limit
}

func parseOffset(s string) int {
	offset, err := strconv.Atoi(s)
	if err != nil || offset < 0 {
		return 0
	}
	return offset
}

type OrderResponse struct {
	ID              string  `json:"id"`
	UserID          int64   `json:"userId"`
	Symbol          string  `json:"symbol"`
	Side            string  `json:"side"`
	Type            string  `json:"type"`
	Quantity        int     `json:"quantity"`
	FilledQuantity  *int    `json:"filledQuantity"`
	AvgFillPrice    *string `json:"avgFillPrice"`
	Status          string  `json:"status"`
	RejectionReason *string `json:"rejectionReason"`
	CreatedAt       string  `json:"createdAt"`
	FilledAt        *string `json:"filledAt"`
	CancelledAt     *string `json:"cancelledAt"`
	UpdatedAt       string  `json:"updatedAt"`
}

type OrderListResponse struct {
	Items  []OrderResponse `json:"items"`
	Total  int             `json:"total"`
	Limit  int             `json:"limit"`
	Offset int             `json:"offset"`
}

func toOrderResponse(o *domain.OrderRecord) OrderResponse {
	resp := OrderResponse{
		ID:        o.ID.String(),
		UserID:    o.UserID,
		Symbol:    o.Symbol,
		Side:      string(o.Side),
		Type:      string(o.OrderType),
		Quantity:  o.Quantity,
		Status:    string(o.Status),
		CreatedAt: o.CreatedAt.Format(time.RFC3339),
		UpdatedAt: o.UpdatedAt.Format(time.RFC3339),
	}
	if o.FilledQuantity != nil {
		resp.FilledQuantity = o.FilledQuantity
	}
	if o.AvgFillPrice != nil {
		resp.AvgFillPrice = o.AvgFillPrice
	}
	if o.RejectionReason != nil {
		resp.RejectionReason = o.RejectionReason
	}
	if o.FilledAt != nil {
		ft := o.FilledAt.Format(time.RFC3339)
		resp.FilledAt = &ft
	}
	if o.CancelledAt != nil {
		ct := o.CancelledAt.Format(time.RFC3339)
		resp.CancelledAt = &ct
	}
	return resp
}
