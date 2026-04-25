package app_test

import (
	"context"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"

	"github.com/vnikolaenko/trading-service/internal/app"
	"github.com/vnikolaenko/trading-service/internal/domain"
	"github.com/vnikolaenko/trading-service/internal/repository"
)

// Mock Repository
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

// Mock Market Client
type MockMarketClient struct {
	mock.Mock
}

func (m *MockMarketClient) GetMarketData(ctx context.Context, symbol string) (decimal.Decimal, int, error) {
	args := m.Called(ctx, symbol)
	return args.Get(0).(decimal.Decimal), args.Int(1), args.Error(2)
}

// Mock Portfolio Client
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

// Mock Kafka Producer
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

func TestCreateOrder_Filled(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	svc := app.NewOrderService(repo, market, portfolio, producer)

	req := app.CreateOrderReq{
		UserID:   1,
		Symbol:   "AAPL",
		Side:     domain.OrderSideBuy,
		Type:     domain.OrderTypeMarket,
		Quantity: 10,
	}

	market.On("GetMarketData", mock.Anything, "AAPL").
		Return(decimal.NewFromInt(150), 100000, nil)
	portfolio.On("GetCashBalance", mock.Anything, int64(1)).
		Return(decimal.NewFromInt(100_000), nil)
	repo.On("CreateOrder", mock.Anything, mock.AnythingOfType("*domain.OrderRecord")).
		Return(nil)
	repo.On("FillOrder", mock.Anything, mock.Anything, mock.Anything, "150", "1500", mock.Anything).
		Return(nil)
	producer.On("ProduceTradingEvent", mock.Anything, mock.MatchedBy(func(e *domain.TradingEvent) bool {
		return e.EventType == "ORDER_FILLED" && e.Symbol == "AAPL" && e.Quantity == 10
	})).Return(nil)

	order, err := svc.CreateOrder(context.Background(), req)
	assert.NoError(t, err)
	assert.Equal(t, domain.OrderStatusFilled, order.Status)

	// Даём время горутине отработать
	time.Sleep(100 * time.Millisecond)

	repo.AssertExpectations(t)
	producer.AssertExpectations(t)
}

func TestCreateOrder_InsufficientFunds(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	svc := app.NewOrderService(repo, market, portfolio, producer)

	req := app.CreateOrderReq{
		UserID:   1,
		Symbol:   "AAPL",
		Side:     domain.OrderSideBuy,
		Quantity: 10,
	}

	market.On("GetMarketData", mock.Anything, "AAPL").
		Return(decimal.NewFromInt(2000), 100000, nil)
	portfolio.On("GetCashBalance", mock.Anything, int64(1)).
		Return(decimal.NewFromInt(100), nil)
	repo.On("CreateOrder", mock.Anything, mock.Anything).
		Return(nil)
	repo.On("UpdateOrderStatus", mock.Anything, mock.Anything, mock.Anything, domain.OrderStatusRejected, mock.Anything).
		Return(nil)
	producer.On("ProduceTradingEvent", mock.Anything, mock.MatchedBy(func(e *domain.TradingEvent) bool {
		return e.EventType == "ORDER_REJECTED" && e.RejectionReason == "INSUFFICIENT_FUNDS"
	})).Return(nil)

	order, err := svc.CreateOrder(context.Background(), req)
	assert.ErrorIs(t, err, app.ErrInsufficientFunds)
	assert.Equal(t, domain.OrderStatusRejected, order.Status)
}

func TestCancelOrder_Success(t *testing.T) {
	repo := new(MockOrderRepo)
	market := new(MockMarketClient)
	portfolio := new(MockPortfolioClient)
	producer := new(MockProducer)

	svc := app.NewOrderService(repo, market, portfolio, producer)

	orderID := uuid.New().String()
	userID := "1"

	existingOrder := &domain.OrderRecord{
		ID:     uuid.MustParse(orderID),
		UserID: 1,
		Status: domain.OrderStatusNew,
	}
	repo.On("GetOrderByID", mock.Anything, orderID, userID).
		Return(existingOrder, nil)
	repo.On("CancelOrder", mock.Anything, orderID, userID).
		Return(nil)
	producer.On("ProduceTradingEvent", mock.Anything, mock.MatchedBy(func(e *domain.TradingEvent) bool {
		return e.EventType == "ORDER_CANCELLED"
	})).Return(nil)

	order, err := svc.CancelOrder(context.Background(), orderID, userID)
	assert.NoError(t, err)
	assert.Equal(t, domain.OrderStatusCancelled, order.Status)
}
