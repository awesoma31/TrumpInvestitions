package handler

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/awesoma31/portfolio-service/models"
	"github.com/awesoma31/portfolio-service/repository"
	"github.com/awesoma31/portfolio-service/service"
	"github.com/gorilla/mux"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type fixedPriceProvider struct{}

func (f *fixedPriceProvider) GetCurrentPrice(symbol string) (decimal.Decimal, error) {
	return decimal.NewFromFloat(100.0), nil
}

func setupHandler() (*Handler, *mux.Router) {
	repo := repository.NewMockRepository()
	price := &fixedPriceProvider{}
	svc := service.NewPortfolioService(repo, price)
	h := NewHandler(svc, repo)
	r := mux.NewRouter()
	h.RegisterRoutes(r)
	return h, r
}

func TestHealthEndpoint(t *testing.T) {
	_, r := setupHandler()
	req := httptest.NewRequest("GET", "/api/v1/system/health", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp models.HealthResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "UP", resp.Status)
	assert.Equal(t, "portfolio-service", resp.Service)
}

func TestReadyEndpoint(t *testing.T) {
	_, r := setupHandler()
	req := httptest.NewRequest("GET", "/api/v1/system/ready", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp models.ReadinessResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "READY", resp.Status)
}

func TestGetPortfolioMissingUserID(t *testing.T) {
	_, r := setupHandler()
	req := httptest.NewRequest("GET", "/api/v1/portfolio", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestGetPortfolioSuccess(t *testing.T) {
	_, r := setupHandler()
	req := httptest.NewRequest("GET", "/api/v1/portfolio", nil)
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp models.PortfolioResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, int64(1), resp.UserID)
	assert.Equal(t, "0.00", resp.CashBalance)
}

func TestDepositEndpoint(t *testing.T) {
	_, r := setupHandler()
	body, _ := json.Marshal(models.BalanceOperationRequest{Amount: "5000.00"})
	req := httptest.NewRequest("POST", "/api/v1/balance/deposit", bytes.NewReader(body))
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp models.BalanceResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "5000.00", resp.Balance)
}

func TestWithdrawInsufficientEndpoint(t *testing.T) {
	_, r := setupHandler()

	// Deposit first
	body, _ := json.Marshal(models.BalanceOperationRequest{Amount: "1000.00"})
	req := httptest.NewRequest("POST", "/api/v1/balance/deposit", bytes.NewReader(body))
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Try to withdraw more
	body, _ = json.Marshal(models.BalanceOperationRequest{Amount: "5000.00"})
	req = httptest.NewRequest("POST", "/api/v1/balance/withdraw", bytes.NewReader(body))
	req.Header.Set("X-User-Id", "1")
	w = httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnprocessableEntity, w.Code)
	var errResp models.ErrorResponse
	json.Unmarshal(w.Body.Bytes(), &errResp)
	assert.Equal(t, "INSUFFICIENT_BALANCE", errResp.Code)
}

func TestDepositInvalidAmount(t *testing.T) {
	_, r := setupHandler()
	body, _ := json.Marshal(models.BalanceOperationRequest{Amount: "-100"})
	req := httptest.NewRequest("POST", "/api/v1/balance/deposit", bytes.NewReader(body))
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestGetPositionNotFound(t *testing.T) {
	_, r := setupHandler()
	req := httptest.NewRequest("GET", "/api/v1/positions/TSLA", nil)
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestListPositionsEmpty(t *testing.T) {
	_, r := setupHandler()
	req := httptest.NewRequest("GET", "/api/v1/positions", nil)
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp models.PositionListResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Empty(t, resp.Items)
}

func TestListOrdersEndpoint(t *testing.T) {
	_, r := setupHandler()
	req := httptest.NewRequest("GET", "/api/v1/orders", nil)
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
}

func TestListTradesEndpoint(t *testing.T) {
	_, r := setupHandler()
	req := httptest.NewRequest("GET", "/api/v1/trades", nil)
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
}

func TestPnlEndpoint(t *testing.T) {
	_, r := setupHandler()

	// Deposit so portfolio exists
	body, _ := json.Marshal(models.BalanceOperationRequest{Amount: "10000.00"})
	req := httptest.NewRequest("POST", "/api/v1/balance/deposit", bytes.NewReader(body))
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	req = httptest.NewRequest("GET", "/api/v1/pnl", nil)
	req.Header.Set("X-User-Id", "1")
	w = httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp models.PortfolioPnlResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "USD", resp.Currency)
}

func TestInvalidUserIDHeader(t *testing.T) {
	_, r := setupHandler()

	req := httptest.NewRequest("GET", "/api/v1/portfolio", nil)
	req.Header.Set("X-User-Id", "not-a-number")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestWithdrawSuccess(t *testing.T) {
	_, r := setupHandler()

	// deposit
	body, _ := json.Marshal(models.BalanceOperationRequest{Amount: "10000.00"})
	req := httptest.NewRequest("POST", "/api/v1/balance/deposit", bytes.NewReader(body))
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// withdraw
	body, _ = json.Marshal(models.BalanceOperationRequest{Amount: "3000.00"})
	req = httptest.NewRequest("POST", "/api/v1/balance/withdraw", bytes.NewReader(body))
	req.Header.Set("X-User-Id", "1")
	w = httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp models.BalanceResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	_ = decimal.RequireFromString(resp.Balance)
	assert.Equal(t, "7000.00", resp.Balance)
}

func TestGetCashBalanceEndpoint(t *testing.T) {
	_, r := setupHandler()

	// deposit first
	body, _ := json.Marshal(models.BalanceOperationRequest{Amount: "8000.00"})
	req := httptest.NewRequest("POST", "/api/v1/balance/deposit", bytes.NewReader(body))
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// get cash balance
	req = httptest.NewRequest("GET", "/api/v1/balance/cash", nil)
	req.Header.Set("X-User-Id", "1")
	w = httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var data map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &data)
	assert.Equal(t, "8000.00", data["balance"])
	assert.Equal(t, "USD", data["currency"])
}

func TestGetCashBalanceMissingUser(t *testing.T) {
	_, r := setupHandler()
	req := httptest.NewRequest("GET", "/api/v1/balance/cash", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestGetAssetQuantityEndpoint(t *testing.T) {
	_, r := setupHandler()
	req := httptest.NewRequest("GET", "/api/v1/assets/AAPL/quantity", nil)
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var data map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &data)
	assert.Equal(t, float64(0), data["quantity"]) // no position yet
	assert.Equal(t, "AAPL", data["symbol"])
}

func TestGetAssetQuantityMissingUser(t *testing.T) {
	_, r := setupHandler()
	req := httptest.NewRequest("GET", "/api/v1/assets/AAPL/quantity", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}
