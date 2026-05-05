package api

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"github.com/vnikolaenko/trading-service/internal/app"
)

func RegisterRoutes(r chi.Router, svc *app.OrderService) {
	r.Route("/api/v1", func(r chi.Router) {
		r.Get("/system/health", healthHandler())
		r.Get("/system/ready", readyHandler())

		r.Group(func(r chi.Router) {
			r.Use(UserIDMiddleware)
			r.Post("/orders", createOrderHandler(svc))
			r.Get("/orders", listOrdersHandler(svc))
			r.Get("/orders/{orderId}", getOrderHandler(svc))
			r.Post("/orders/{orderId}/cancel", cancelOrderHandler(svc))
			r.Get("/trades", listTradesHandler(svc))
			r.Get("/trades/{tradeId}", getTradeHandler(svc))
		})
	})
}

func writeJSON(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, code, msg, traceID string) {
	resp := ErrorResponse{
		Code:    code,
		Message: msg,
		TraceID: traceID,
	}
	writeJSON(w, status, resp)
}

type ErrorResponse struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	TraceID string `json:"traceId"`
}

func newTraceID() string {
	return uuid.New().String()[:8]
}
