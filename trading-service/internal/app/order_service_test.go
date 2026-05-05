package app_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"

	"github.com/vnikolaenko/trading-service/internal/api"
	"github.com/vnikolaenko/trading-service/internal/app"
	"github.com/vnikolaenko/trading-service/internal/domain"
	"github.com/vnikolaenko/trading-service/internal/repository"
)

// =============================================================================
// Моки
// =============================================================================

type MockOrderRepo struct {
	mock.Mock
}

func (m *MockOrderRepo) CreateOrder(ctx context.Context, order *domain.OrderRecord) error {
	args := m.Called(ctx, order)
	return args.Error(0)
}

func (m *MockOrderRepo) GetOrderByID(ctx context.Context, orderID, userID string) (*domain.OrderRecord, error) {
	args := m.Called(ctx, orderID, userID)
	rec := args.Get(0)
	if rec == nil {
		return nil, args.Error(1)
	}
	return rec.(*domain.OrderRecord), args.Error(1)
}

func (m *MockOrderRepo) ListOrders(ctx context.Context, userID int64, filters repository.OrderFilter) ([]domain.OrderRecord, int, error) {
	args := m.Called(ctx, userID, filters)
	return args.Get(0).([]domain.OrderRecord), args.Int(1), args.Error(2)
}

func (m *MockOrderRepo) UpdateOrderStatus(ctx context.Context, orderID, userID string, status domain.OrderStatus, reason *string) error {
	args := m.Called(ctx, orderID, userID, status, reason)
	return args.Error(0)
}

func (m *MockOrderRepo) FillOrder(ctx context.Context, orderID, tradeID, price, gross string, t time.Time) error {
	args := m.Called(ctx, orderID, tradeID, price, gross, t)
	return args.Error(0)
}

func (m *MockOrderRepo) CancelOrder(ctx context.Context, orderID, userID string) error {
	args := m.Called(ctx, orderID, userID)
	return args.Error(0)
}

func (m *MockOrderRepo) GetTradeByID(ctx context.Context, tradeID, userID string) (*domain.OrderRecord, error) {
	args := m.Called(ctx, tradeID, userID)
	rec := args.Get(0)
	if rec == nil {
		return nil, args.Error(1)
	}
	return rec.(*domain.OrderRecord), args.Error(1)
}

func (m *MockOrderRepo) ListTrades(ctx context.Context, userID int64, filters repository.TradeFilter) ([]domain.OrderRecord, int, error) {
	args := m.Called(ctx, userID, filters)
	return args.Get(0).([]domain.OrderRecord), args.Int(1), args.Error(2)
}

type MockMarketClient struct {
	mock.Mock
}

func (m *MockMarketClient) GetMarketData(ctx context.Context, symbol string, side domain.OrderSide) (decimal.Decimal, int, error) {
	args := m.Called(ctx, symbol, side)
	return args.Get(0).(decimal.Decimal), args.Int(1), args.Error(2)
}

type MockPortfolioClient struct {
	mock.Mock
}

func (m *MockPortfolioClient) GetCashBalance(ctx context.Context, userID int64) (decimal.Decimal, error) {
	args := m.Called(ctx, userID)
	return args.Get(0).(decimal.Decimal), args.Error(1)
}

func (m *MockPortfolioClient) GetAssetQuantity(ctx context.Context, userID int64, symbol string) (int, error) {
	args := m.Called(ctx, userID, symbol)
	return args.Int(0), args.Error(1)
}

type MockProducer struct {
	mock.Mock
}

func (m *MockProducer) ProduceTradingEvent(ctx context.Context, event *domain.TradingEvent) error {
	args := m.Called(ctx, event)
	return args.Error(0)
}

func (m *MockProducer) Close() error {
	return nil
}

// =============================================================================
// Вспомогательные функции
// =============================================================================

func newTestRouter(svc *app.OrderService) *chi.Mux {
	r := chi.NewRouter()
	r.Use(api.CORSMiddleware)
	r.Use(api.UserIDMiddleware)
	api.RegisterRoutes(r, svc)
	return r
}

func makeRequest(router *chi.Mux, method, path string, body interface{}, headers map[string]string) *httptest.ResponseRecorder {
	var reqBody []byte
	if body != nil {
		reqBody, _ = json.Marshal(body)
	}
	req := httptest.NewRequest(method, path, bytes.NewReader(reqBody))
	req.Header.Set("Content-Type", "application/json")
	for key, value := range headers {
		req.Header.Set(key, value)
	}
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	return w
}

func setupMocksForSuccessfulBuy(repo *MockOrderRepo, market *MockMarketClient, portfolio *MockPortfolioClient, producer *MockProducer) {
	market.On("GetMarketData", mock.Anything, "AAPL", domain.OrderSideBuy).
		Return(decimal.NewFromInt(150), 100000, nil)
	portfolio.On("GetCashBalance", mock.Anything, int64(1)).
		Return(decimal.NewFromInt(100000), nil)
	repo.On("CreateOrder", mock.Anything, mock.AnythingOfType("*domain.OrderRecord")).
		Return(nil)
	repo.On("FillOrder", mock.Anything, mock.Anything, mock.Anything, "150", "1500", mock.Anything).
		Return(nil)
	producer.On("ProduceTradingEvent", mock.Anything, mock.Anything).
		Return(nil)
}

// =============================================================================
// ТЕСТЫ: POST /orders (создание заявки)
// =============================================================================

// 1. Успешная покупка (BUY)
func TestCreateOrder_SuccessfulBuy(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	setupMocksForSuccessfulBuy(repo, market, portfolio, producer)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	body := map[string]interface{}{
		"symbol":   "AAPL",
		"side":     "BUY",
		"type":     "MARKET",
		"quantity": 10,
	}

	w := makeRequest(router, "POST", "/api/v1/orders", body, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusCreated, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, "FILLED", response["status"])
	assert.Equal(t, "AAPL", response["symbol"])
	assert.Equal(t, "BUY", response["side"])

	time.Sleep(50 * time.Millisecond)
	repo.AssertExpectations(t)
	producer.AssertExpectations(t)
}

// 2. Успешная продажа (SELL)
func TestCreateOrder_SuccessfulSell(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	market.On("GetMarketData", mock.Anything, "TSLA", domain.OrderSideSell).
		Return(decimal.NewFromInt(200), 50000, nil)
	portfolio.On("GetAssetQuantity", mock.Anything, int64(2), "TSLA").
		Return(100, nil)
	repo.On("CreateOrder", mock.Anything, mock.AnythingOfType("*domain.OrderRecord")).
		Return(nil)
	repo.On("FillOrder", mock.Anything, mock.Anything, mock.Anything, "200", "1000", mock.Anything).
		Return(nil)
	producer.On("ProduceTradingEvent", mock.Anything, mock.Anything).
		Return(nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	body := map[string]interface{}{
		"symbol":   "TSLA",
		"side":     "SELL",
		"type":     "MARKET",
		"quantity": 5,
	}

	w := makeRequest(router, "POST", "/api/v1/orders", body, map[string]string{
		"X-User-Id": "2",
	})

	assert.Equal(t, http.StatusCreated, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, "FILLED", response["status"])
	assert.Equal(t, "SELL", response["side"])

	time.Sleep(50 * time.Millisecond)
	repo.AssertExpectations(t)
	producer.AssertExpectations(t)
}

// 3. Недостаточно средств (INSUFFICIENT_FUNDS)
func TestCreateOrder_InsufficientFunds(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	market.On("GetMarketData", mock.Anything, "AAPL", domain.OrderSideBuy).
		Return(decimal.NewFromInt(2000), 100000, nil)
	portfolio.On("GetCashBalance", mock.Anything, int64(1)).
		Return(decimal.NewFromInt(100), nil)
	repo.On("CreateOrder", mock.Anything, mock.Anything).
		Return(nil)
	repo.On("UpdateOrderStatus", mock.Anything, mock.Anything, mock.Anything, domain.OrderStatusRejected, mock.Anything).
		Return(nil)
	producer.On("ProduceTradingEvent", mock.Anything, mock.Anything).
		Return(nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	body := map[string]interface{}{
		"symbol":   "AAPL",
		"side":     "BUY",
		"type":     "MARKET",
		"quantity": 10,
	}

	w := makeRequest(router, "POST", "/api/v1/orders", body, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusUnprocessableEntity, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, "INSUFFICIENT_FUNDS", response["code"])
}

// 4. Недостаточно активов (INSUFFICIENT_ASSETS)
func TestCreateOrder_InsufficientAssets(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	market.On("GetMarketData", mock.Anything, "GOOGL", domain.OrderSideSell).
		Return(decimal.NewFromInt(100), 100000, nil)
	portfolio.On("GetAssetQuantity", mock.Anything, int64(2), "GOOGL").
		Return(10, nil)
	repo.On("CreateOrder", mock.Anything, mock.Anything).
		Return(nil)
	repo.On("UpdateOrderStatus", mock.Anything, mock.Anything, mock.Anything, domain.OrderStatusRejected, mock.Anything).
		Return(nil)
	producer.On("ProduceTradingEvent", mock.Anything, mock.Anything).
		Return(nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	body := map[string]interface{}{
		"symbol":   "GOOGL",
		"side":     "SELL",
		"type":     "MARKET",
		"quantity": 50,
	}

	w := makeRequest(router, "POST", "/api/v1/orders", body, map[string]string{
		"X-User-Id": "2",
	})

	assert.Equal(t, http.StatusUnprocessableEntity, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, "INSUFFICIENT_ASSETS", response["code"])
}

// 5. Недостаточный рыночный объём (INSUFFICIENT_MARKET_VOLUME)
func TestCreateOrder_InsufficientMarketVolume(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	// Объём рынка 5, а запрашивают 100
	market.On("GetMarketData", mock.Anything, "AAPL", domain.OrderSideBuy).
		Return(decimal.NewFromInt(150), 5, nil)
	// GetCashBalance может быть вызван параллельно с GetMarketData до проверки объёма
	portfolio.On("GetCashBalance", mock.Anything, int64(1)).
		Maybe().Return(decimal.NewFromInt(100000), nil)
	repo.On("CreateOrder", mock.Anything, mock.Anything).
		Return(nil)
	repo.On("UpdateOrderStatus", mock.Anything, mock.Anything, mock.Anything, domain.OrderStatusRejected, mock.Anything).
		Return(nil)
	producer.On("ProduceTradingEvent", mock.Anything, mock.Anything).
		Return(nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	body := map[string]interface{}{
		"symbol":   "AAPL",
		"side":     "BUY",
		"type":     "MARKET",
		"quantity": 100,
	}

	w := makeRequest(router, "POST", "/api/v1/orders", body, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusUnprocessableEntity, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, "INSUFFICIENT_MARKET_VOLUME", response["code"])
}

// 6. Отсутствует заголовок X-User-Id
func TestCreateOrder_MissingUserId(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	body := map[string]interface{}{
		"symbol":   "AAPL",
		"side":     "BUY",
		"type":     "MARKET",
		"quantity": 10,
	}

	w := makeRequest(router, "POST", "/api/v1/orders", body, map[string]string{})
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

// 7. Некорректный JSON
func TestCreateOrder_InvalidJSON(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	req := httptest.NewRequest("POST", "/api/v1/orders", bytes.NewReader([]byte("not a json")))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-User-Id", "1")
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

// 8. Некорректный X-User-Id (не число)
func TestCreateOrder_InvalidUserId(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	body := map[string]interface{}{
		"symbol":   "AAPL",
		"side":     "BUY",
		"type":     "MARKET",
		"quantity": 10,
	}

	w := makeRequest(router, "POST", "/api/v1/orders", body, map[string]string{
		"X-User-Id": "abc",
	})

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

// =============================================================================
// ТЕСТЫ: GET /orders (список заявок)
// =============================================================================

func TestListOrders_Success(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	now := time.Now()
	orders := []domain.OrderRecord{
		{
			ID:        uuid.New(),
			UserID:    1,
			Symbol:    "AAPL",
			Side:      domain.OrderSideBuy,
			OrderType: domain.OrderTypeMarket,
			Quantity:  10,
			Status:    domain.OrderStatusFilled,
			CreatedAt: now,
			UpdatedAt: now,
		},
	}

	repo.On("ListOrders", mock.Anything, int64(1), mock.Anything).
		Return(orders, 1, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "GET", "/api/v1/orders?limit=10&offset=0", nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusOK, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, float64(1), response["total"])
	items := response["items"].([]interface{})
	assert.Equal(t, 1, len(items))
}

func TestListOrders_WithFilters(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	repo.On("ListOrders", mock.Anything, int64(1), mock.MatchedBy(func(f repository.OrderFilter) bool {
		return f.Status != nil && *f.Status == domain.OrderStatusFilled &&
			f.Symbol != nil && *f.Symbol == "AAPL"
	})).Return([]domain.OrderRecord{}, 0, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "GET", "/api/v1/orders?status=FILLED&symbol=AAPL", nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusOK, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, float64(0), response["total"])
}

func TestListOrders_EmptyList(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	repo.On("ListOrders", mock.Anything, int64(1), mock.Anything).
		Return([]domain.OrderRecord{}, 0, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "GET", "/api/v1/orders", nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusOK, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, float64(0), response["total"])
	items := response["items"].([]interface{})
	assert.Equal(t, 0, len(items))
}

func TestListOrders_DefaultLimit(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	repo.On("ListOrders", mock.Anything, int64(1), mock.MatchedBy(func(f repository.OrderFilter) bool {
		return f.Limit == 50 && f.Offset == 0
	})).Return([]domain.OrderRecord{}, 0, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	makeRequest(router, "GET", "/api/v1/orders", nil, map[string]string{
		"X-User-Id": "1",
	})

	repo.AssertExpectations(t)
}

// =============================================================================
// ТЕСТЫ: GET /orders/{orderId} (заявка по ID)
// =============================================================================

func TestGetOrder_Success(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	orderID := uuid.New()
	order := &domain.OrderRecord{
		ID:        orderID,
		UserID:    1,
		Symbol:    "AAPL",
		Side:      domain.OrderSideBuy,
		OrderType: domain.OrderTypeMarket,
		Quantity:  10,
		Status:    domain.OrderStatusFilled,
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}

	repo.On("GetOrderByID", mock.Anything, orderID.String(), "1").
		Return(order, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "GET", "/api/v1/orders/"+orderID.String(), nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusOK, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, orderID.String(), response["id"])
}

func TestGetOrder_NotFound(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	orderID := uuid.New()
	repo.On("GetOrderByID", mock.Anything, orderID.String(), "1").
		Return(nil, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "GET", "/api/v1/orders/"+orderID.String(), nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusNotFound, w.Code)
}

// =============================================================================
// ТЕСТЫ: POST /orders/{orderId}/cancel (отмена заявки)
// =============================================================================

func TestCancelOrder_Success(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	orderID := uuid.New()
	order := &domain.OrderRecord{
		ID:     orderID,
		UserID: 1,
		Status: domain.OrderStatusNew,
	}

	repo.On("GetOrderByID", mock.Anything, orderID.String(), "1").
		Return(order, nil)
	repo.On("CancelOrder", mock.Anything, orderID.String(), "1").
		Return(nil)
	producer.On("ProduceTradingEvent", mock.Anything, mock.Anything).
		Return(nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "POST", "/api/v1/orders/"+orderID.String()+"/cancel", nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusOK, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, "CANCELLED", response["status"])

	time.Sleep(50 * time.Millisecond)
	producer.AssertExpectations(t)
}

func TestCancelOrder_NotFound(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	orderID := uuid.New()
	repo.On("GetOrderByID", mock.Anything, orderID.String(), "1").
		Return(nil, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "POST", "/api/v1/orders/"+orderID.String()+"/cancel", nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestCancelOrder_AlreadyFilled(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	orderID := uuid.New()
	order := &domain.OrderRecord{
		ID:     orderID,
		UserID: 1,
		Status: domain.OrderStatusFilled,
	}

	repo.On("GetOrderByID", mock.Anything, orderID.String(), "1").
		Return(order, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "POST", "/api/v1/orders/"+orderID.String()+"/cancel", nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusConflict, w.Code)
}

// =============================================================================
// ТЕСТЫ: GET /trades (список сделок)
// =============================================================================

func TestListTrades_Success(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	now := time.Now()
	tradeID := uuid.New()
	orderID := uuid.New()
	price := "150"
	gross := "1500"

	trades := []domain.OrderRecord{
		{
			ID:               orderID,
			UserID:           1,
			Symbol:           "AAPL",
			Side:             domain.OrderSideBuy,
			Quantity:         10,
			Status:           domain.OrderStatusFilled,
			TradeID:          &tradeID,
			TradePrice:       &price,
			TradeGrossAmount: &gross,
			TradeExecutedAt:  &now,
			CreatedAt:        now,
			UpdatedAt:        now,
		},
	}

	repo.On("ListTrades", mock.Anything, int64(1), mock.Anything).
		Return(trades, 1, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "GET", "/api/v1/trades", nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusOK, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, float64(1), response["total"])
	items := response["items"].([]interface{})
	assert.Equal(t, 1, len(items))
}

func TestListTrades_WithFilters(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	repo.On("ListTrades", mock.Anything, int64(1), mock.MatchedBy(func(f repository.TradeFilter) bool {
		return f.Symbol != nil && *f.Symbol == "AAPL" &&
			f.Side != nil && *f.Side == domain.OrderSideBuy
	})).Return([]domain.OrderRecord{}, 0, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "GET", "/api/v1/trades?symbol=AAPL&side=BUY", nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusOK, w.Code)
	repo.AssertExpectations(t)
}

func TestListTrades_EmptyList(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	repo.On("ListTrades", mock.Anything, int64(1), mock.Anything).
		Return([]domain.OrderRecord{}, 0, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "GET", "/api/v1/trades", nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusOK, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, float64(0), response["total"])
	items := response["items"].([]interface{})
	assert.Equal(t, 0, len(items))
}

// =============================================================================
// ТЕСТЫ: GET /trades/{tradeId} (сделка по ID)
// =============================================================================

func TestGetTrade_Success(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	tradeID := uuid.New()
	orderID := uuid.New()
	now := time.Now()
	price := "150"
	gross := "1500"

	trade := &domain.OrderRecord{
		ID:               orderID,
		UserID:           1,
		Symbol:           "AAPL",
		Side:             domain.OrderSideBuy,
		Quantity:         10,
		Status:           domain.OrderStatusFilled,
		TradeID:          &tradeID,
		TradePrice:       &price,
		TradeGrossAmount: &gross,
		TradeExecutedAt:  &now,
		CreatedAt:        now,
		UpdatedAt:        now,
	}

	repo.On("GetTradeByID", mock.Anything, tradeID.String(), "1").
		Return(trade, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "GET", "/api/v1/trades/"+tradeID.String(), nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusOK, w.Code)

	var response map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &response)
	assert.Equal(t, tradeID.String(), response["id"])
	assert.Equal(t, orderID.String(), response["orderId"])
}

func TestGetTrade_NotFound(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	tradeID := uuid.New()
	repo.On("GetTradeByID", mock.Anything, tradeID.String(), "1").
		Return(nil, nil)

	svc := app.NewOrderService(repo, market, portfolio, producer)
	router := newTestRouter(svc)

	w := makeRequest(router, "GET", "/api/v1/trades/"+tradeID.String(), nil, map[string]string{
		"X-User-Id": "1",
	})

	assert.Equal(t, http.StatusNotFound, w.Code)
}
