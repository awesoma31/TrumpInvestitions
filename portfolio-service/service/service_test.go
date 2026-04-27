package service

import (
	"context"
	"testing"

	"github.com/awesoma31/portfolio-service/models"
	"github.com/awesoma31/portfolio-service/repository"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type fixedPriceProvider struct {
	prices map[string]decimal.Decimal
}

func (f *fixedPriceProvider) GetCurrentPrice(symbol string) (decimal.Decimal, error) {
	if p, ok := f.prices[symbol]; ok {
		return p, nil
	}
	return decimal.NewFromFloat(100.0), nil
}

func newTestService() (*PortfolioService, *repository.MockRepository) {
	repo := repository.NewMockRepository()
	price := &fixedPriceProvider{prices: map[string]decimal.Decimal{
		"AAPL": decimal.NewFromFloat(190.0),
		"GOOG": decimal.NewFromFloat(140.0),
	}}
	return NewPortfolioService(repo, price), repo
}

func TestDeposit(t *testing.T) {
	svc, _ := newTestService()
	ctx := context.Background()

	resp, err := svc.Deposit(ctx, 1, decimal.NewFromFloat(10000))
	require.NoError(t, err)
	assert.Equal(t, "10000.00", resp.Balance)
	assert.Equal(t, int64(1), resp.UserID)
	assert.Equal(t, "USD", resp.Currency)
}

func TestDepositMultiple(t *testing.T) {
	svc, _ := newTestService()
	ctx := context.Background()

	svc.Deposit(ctx, 1, decimal.NewFromFloat(5000))
	resp, err := svc.Deposit(ctx, 1, decimal.NewFromFloat(3000))
	require.NoError(t, err)
	assert.Equal(t, "8000.00", resp.Balance)
}

func TestDepositNegativeAmount(t *testing.T) {
	svc, _ := newTestService()
	ctx := context.Background()

	_, err := svc.Deposit(ctx, 1, decimal.NewFromFloat(-100))
	assert.Error(t, err)
}

func TestWithdraw(t *testing.T) {
	svc, _ := newTestService()
	ctx := context.Background()

	svc.Deposit(ctx, 1, decimal.NewFromFloat(10000))
	resp, err := svc.Withdraw(ctx, 1, decimal.NewFromFloat(3000))
	require.NoError(t, err)
	assert.Equal(t, "7000.00", resp.Balance)
}

func TestWithdrawInsufficientBalance(t *testing.T) {
	svc, _ := newTestService()
	ctx := context.Background()

	svc.Deposit(ctx, 1, decimal.NewFromFloat(1000))
	_, err := svc.Withdraw(ctx, 1, decimal.NewFromFloat(5000))
	assert.Error(t, err)
	_, ok := err.(*InsufficientBalanceError)
	assert.True(t, ok)
}

func TestGetPortfolioEmpty(t *testing.T) {
	svc, _ := newTestService()
	ctx := context.Background()

	resp, err := svc.GetPortfolio(ctx, 42)
	require.NoError(t, err)
	assert.Equal(t, int64(42), resp.UserID)
	assert.Equal(t, "0.00", resp.CashBalance)
	assert.Equal(t, "0.00", resp.TotalMarketValue)
	assert.Empty(t, resp.Positions)
}

func TestGetPortfolioWithPositions(t *testing.T) {
	svc, repo := newTestService()
	ctx := context.Background()

	svc.Deposit(ctx, 1, decimal.NewFromFloat(50000))
	// Simulate a position
	repo.UpsertPosition(ctx, 1, "AAPL", 10, decimal.NewFromFloat(185.0), decimal.Zero)

	resp, err := svc.GetPortfolio(ctx, 1)
	require.NoError(t, err)
	assert.Equal(t, "50000.00", resp.CashBalance)
	assert.Len(t, resp.Positions, 1)
	assert.Equal(t, "AAPL", resp.Positions[0].Symbol)
	assert.Equal(t, "190.00", resp.Positions[0].CurrentPrice) // from fixedPriceProvider
	assert.Equal(t, "1900.00", resp.Positions[0].MarketValue) // 10 * 190
	assert.Equal(t, "50.00", resp.Positions[0].UnrealizedPnl) // (190-185)*10
}

func TestGetPositionBySymbol(t *testing.T) {
	svc, repo := newTestService()
	ctx := context.Background()

	repo.UpsertPosition(ctx, 1, "GOOG", 5, decimal.NewFromFloat(130.0), decimal.NewFromFloat(20.0))

	resp, err := svc.GetPositionBySymbol(ctx, 1, "GOOG")
	require.NoError(t, err)
	require.NotNil(t, resp)
	assert.Equal(t, "GOOG", resp.Symbol)
	assert.Equal(t, 5, resp.Quantity)
	assert.Equal(t, "140.00", resp.CurrentPrice)
	assert.Equal(t, "700.00", resp.MarketValue)        // 5 * 140
	assert.Equal(t, "50.00", resp.UnrealizedPnl)       // (140-130)*5
	assert.Equal(t, "20.00", resp.RealizedPnl)
	assert.Equal(t, "70.00", resp.TotalPnl)            // 20 + 50
}

func TestGetPositionBySymbolNotFound(t *testing.T) {
	svc, _ := newTestService()
	ctx := context.Background()

	resp, err := svc.GetPositionBySymbol(ctx, 1, "TSLA")
	require.NoError(t, err)
	assert.Nil(t, resp)
}

func TestGetPnl(t *testing.T) {
	svc, repo := newTestService()
	ctx := context.Background()

	svc.Deposit(ctx, 1, decimal.NewFromFloat(50000))
	repo.UpsertPosition(ctx, 1, "AAPL", 10, decimal.NewFromFloat(185.0), decimal.NewFromFloat(15.0))

	resp, err := svc.GetPnl(ctx, 1)
	require.NoError(t, err)
	assert.Equal(t, "15.00", resp.RealizedPnl)
	assert.Equal(t, "50.00", resp.UnrealizedPnl) // (190-185)*10
	assert.Equal(t, "65.00", resp.TotalPnl)
	assert.Equal(t, "USD", resp.Currency)
}

func TestProcessTradeExecutedBuy(t *testing.T) {
	svc, _ := newTestService()
	ctx := context.Background()

	event := &models.TradingEvent{
		EventType: "TRADE_EXECUTED",
		UserID:    1,
		OrderID:   "00000000-0000-0000-0000-000000000001",
		TradeID:   "00000000-0000-0000-0000-000000000002",
		Symbol:    "AAPL",
		Side:      "BUY",
		Quantity:  10,
		Price:     "185.00",
		GrossAmount: "1850.00",
		Timestamp: "2024-01-01T00:00:00Z",
	}

	// Insert the order first so trade references it
	err := svc.ProcessTradingEvent(ctx, event)
	require.NoError(t, err)

	pos, err := svc.GetPositionBySymbol(ctx, 1, "AAPL")
	require.NoError(t, err)
	require.NotNil(t, pos)
	assert.Equal(t, 10, pos.Quantity)
	assert.Equal(t, "185.00", pos.AvgPrice)
}

func TestProcessTradeExecutedSell(t *testing.T) {
	svc, repo := newTestService()
	ctx := context.Background()

	// Set up existing position
	repo.UpsertPosition(ctx, 1, "AAPL", 10, decimal.NewFromFloat(185.0), decimal.Zero)

	event := &models.TradingEvent{
		EventType: "TRADE_EXECUTED",
		UserID:    1,
		OrderID:   "00000000-0000-0000-0000-000000000001",
		TradeID:   "00000000-0000-0000-0000-000000000003",
		Symbol:    "AAPL",
		Side:      "SELL",
		Quantity:  5,
		Price:     "195.00",
		GrossAmount: "975.00",
		Timestamp: "2024-01-01T00:00:00Z",
	}

	err := svc.ProcessTradingEvent(ctx, event)
	require.NoError(t, err)

	pos, err := svc.GetPositionBySymbol(ctx, 1, "AAPL")
	require.NoError(t, err)
	require.NotNil(t, pos)
	assert.Equal(t, 5, pos.Quantity)
	assert.Equal(t, "50.00", pos.RealizedPnl) // (195-185)*5
}

func TestProcessUnknownEventType(t *testing.T) {
	svc, _ := newTestService()
	ctx := context.Background()

	event := &models.TradingEvent{EventType: "UNKNOWN"}
	err := svc.ProcessTradingEvent(ctx, event)
	assert.Error(t, err)
}

func TestListOrdersEmpty(t *testing.T) {
	svc, _ := newTestService()
	ctx := context.Background()

	resp, err := svc.ListOrders(ctx, 1, nil, nil, 50, 0)
	require.NoError(t, err)
	assert.Equal(t, 0, resp.Total)
	assert.Empty(t, resp.Items)
}

func TestListTradesEmpty(t *testing.T) {
	svc, _ := newTestService()
	ctx := context.Background()

	resp, err := svc.ListTrades(ctx, 1, nil, nil, 50, 0)
	require.NoError(t, err)
	assert.Equal(t, 0, resp.Total)
	assert.Empty(t, resp.Items)
}
