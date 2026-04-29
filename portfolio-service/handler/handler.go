package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/awesoma31/portfolio-service/models"
	"github.com/awesoma31/portfolio-service/repository"
	"github.com/awesoma31/portfolio-service/service"
	"github.com/google/uuid"
	"github.com/gorilla/mux"
	"github.com/shopspring/decimal"
)

type Handler struct {
	svc  *service.PortfolioService
	repo repository.Repository
}

func NewHandler(svc *service.PortfolioService, repo repository.Repository) *Handler {
	return &Handler{svc: svc, repo: repo}
}

func (h *Handler) RegisterRoutes(r *mux.Router) {
	api := r.PathPrefix("/api/v1").Subrouter()

	api.HandleFunc("/portfolio", h.GetPortfolio).Methods("GET")
	api.HandleFunc("/positions", h.ListPositions).Methods("GET")
	api.HandleFunc("/positions/{symbol}", h.GetPositionBySymbol).Methods("GET")
	api.HandleFunc("/pnl", h.GetPnl).Methods("GET")
	api.HandleFunc("/balance/deposit", h.Deposit).Methods("POST")
	api.HandleFunc("/balance/withdraw", h.Withdraw).Methods("POST")
	api.HandleFunc("/orders", h.ListOrders).Methods("GET")
	api.HandleFunc("/trades", h.ListTrades).Methods("GET")

	api.HandleFunc("/balance/cash", h.GetCashBalance).Methods("GET")
	api.HandleFunc("/assets/{symbol}/quantity", h.GetAssetQuantity).Methods("GET")

	api.HandleFunc("/system/health", h.Health).Methods("GET")
	api.HandleFunc("/system/ready", h.Ready).Methods("GET")
}

func (h *Handler) GetPortfolio(w http.ResponseWriter, r *http.Request) {
	userID, ok := getUserID(r)
	if !ok {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Missing or invalid X-User-Id header")
		return
	}
	resp, err := h.svc.GetPortfolio(r.Context(), userID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (h *Handler) ListPositions(w http.ResponseWriter, r *http.Request) {
	userID, ok := getUserID(r)
	if !ok {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Missing or invalid X-User-Id header")
		return
	}
	var symbol *string
	if s := r.URL.Query().Get("symbol"); s != "" {
		symbol = &s
	}
	resp, err := h.svc.GetPositions(r.Context(), userID, symbol)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (h *Handler) GetPositionBySymbol(w http.ResponseWriter, r *http.Request) {
	userID, ok := getUserID(r)
	if !ok {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Missing or invalid X-User-Id header")
		return
	}
	symbol := mux.Vars(r)["symbol"]
	resp, err := h.svc.GetPositionBySymbol(r.Context(), userID, symbol)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	if resp == nil {
		writeError(w, http.StatusNotFound, "NOT_FOUND", "Position not found")
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (h *Handler) GetPnl(w http.ResponseWriter, r *http.Request) {
	userID, ok := getUserID(r)
	if !ok {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Missing or invalid X-User-Id header")
		return
	}
	resp, err := h.svc.GetPnl(r.Context(), userID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (h *Handler) Deposit(w http.ResponseWriter, r *http.Request) {
	userID, ok := getUserID(r)
	if !ok {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Missing or invalid X-User-Id header")
		return
	}
	var req models.BalanceOperationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Invalid request body")
		return
	}
	amount, err := decimal.NewFromString(req.Amount)
	if err != nil || amount.LessThanOrEqual(decimal.Zero) {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Invalid amount")
		return
	}
	resp, err := h.svc.Deposit(r.Context(), userID, amount)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (h *Handler) Withdraw(w http.ResponseWriter, r *http.Request) {
	userID, ok := getUserID(r)
	if !ok {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Missing or invalid X-User-Id header")
		return
	}
	var req models.BalanceOperationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Invalid request body")
		return
	}
	amount, err := decimal.NewFromString(req.Amount)
	if err != nil || amount.LessThanOrEqual(decimal.Zero) {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Invalid amount")
		return
	}
	resp, err := h.svc.Withdraw(r.Context(), userID, amount)
	if err != nil {
		if _, ok := err.(*service.InsufficientBalanceError); ok {
			writeError(w, http.StatusUnprocessableEntity, "INSUFFICIENT_BALANCE", "Not enough balance to withdraw requested amount")
			return
		}
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (h *Handler) ListOrders(w http.ResponseWriter, r *http.Request) {
	userID, ok := getUserID(r)
	if !ok {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Missing or invalid X-User-Id header")
		return
	}
	q := r.URL.Query()
	var status, symbol *string
	if s := q.Get("status"); s != "" {
		status = &s
	}
	if s := q.Get("symbol"); s != "" {
		symbol = &s
	}
	limit := queryInt(q.Get("limit"), 50)
	offset := queryInt(q.Get("offset"), 0)

	resp, err := h.svc.ListOrders(r.Context(), userID, status, symbol, limit, offset)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (h *Handler) ListTrades(w http.ResponseWriter, r *http.Request) {
	userID, ok := getUserID(r)
	if !ok {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Missing or invalid X-User-Id header")
		return
	}
	q := r.URL.Query()
	var symbol, side *string
	if s := q.Get("symbol"); s != "" {
		symbol = &s
	}
	if s := q.Get("side"); s != "" {
		side = &s
	}
	limit := queryInt(q.Get("limit"), 50)
	offset := queryInt(q.Get("offset"), 0)

	resp, err := h.svc.ListTrades(r.Context(), userID, symbol, side, limit, offset)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (h *Handler) GetCashBalance(w http.ResponseWriter, r *http.Request) {
	userID, ok := getUserID(r)
	if !ok {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Missing or invalid X-User-Id header")
		return
	}
	balance, err := h.svc.GetCashBalance(r.Context(), userID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"userId":   userID,
		"balance":  balance.StringFixed(2),
		"currency": "USD",
	})
}

func (h *Handler) GetAssetQuantity(w http.ResponseWriter, r *http.Request) {
	userID, ok := getUserID(r)
	if !ok {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Missing or invalid X-User-Id header")
		return
	}
	symbol := mux.Vars(r)["symbol"]
	if symbol == "" {
		writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", "Missing symbol")
		return
	}
	qty, err := h.svc.GetAssetQuantity(r.Context(), userID, symbol)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"userId":   userID,
		"symbol":   symbol,
		"quantity": qty,
	})
}

func (h *Handler) Health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, models.HealthResponse{
		Status:    "UP",
		Service:   "portfolio-service",
		Timestamp: time.Now().UTC().Format(time.RFC3339),
	})
}

func (h *Handler) Ready(w http.ResponseWriter, r *http.Request) {
	deps := []models.DependencyStatus{}
	pgStatus := "UP"
	if err := h.repo.Ping(r.Context()); err != nil {
		pgStatus = "DOWN"
	}
	deps = append(deps, models.DependencyStatus{Name: "postgres", Status: pgStatus})

	overall := "READY"
	for _, d := range deps {
		if d.Status == "DOWN" {
			overall = "NOT_READY"
			break
		}
	}

	code := http.StatusOK
	if overall != "READY" {
		code = http.StatusServiceUnavailable
	}

	writeJSON(w, code, models.ReadinessResponse{
		Status:       overall,
		Service:      "portfolio-service",
		Dependencies: deps,
		Timestamp:    time.Now().UTC().Format(time.RFC3339),
	})
}

// --- helpers ---

func getUserID(r *http.Request) (int64, bool) {
	h := r.Header.Get("X-User-Id")
	if h == "" {
		return 0, false
	}
	id, err := strconv.ParseInt(h, 10, 64)
	if err != nil {
		return 0, false
	}
	return id, true
}

func queryInt(s string, def int) int {
	if s == "" {
		return def
	}
	v, err := strconv.Atoi(s)
	if err != nil {
		return def
	}
	return v
}

func writeJSON(w http.ResponseWriter, code int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(data)
}

func writeError(w http.ResponseWriter, code int, errCode, msg string) {
	writeJSON(w, code, models.ErrorResponse{
		Code:    errCode,
		Message: msg,
		TraceID: uuid.New().String()[:16],
	})
}
