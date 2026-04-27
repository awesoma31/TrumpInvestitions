package service

import (
	"context"
	"fmt"
	"time"

	"github.com/awesoma31/portfolio-service/models"
	"github.com/awesoma31/portfolio-service/repository"
	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

type PriceProvider interface {
	GetCurrentPrice(symbol string) (decimal.Decimal, error)
}

// StubPriceProvider returns a fixed price for MVP
type StubPriceProvider struct{}

func (s *StubPriceProvider) GetCurrentPrice(symbol string) (decimal.Decimal, error) {
	return decimal.NewFromFloat(100.0), nil
}

type PortfolioService struct {
	repo  repository.Repository
	price PriceProvider
}

func NewPortfolioService(repo repository.Repository, price PriceProvider) *PortfolioService {
	return &PortfolioService{repo: repo, price: price}
}

func (s *PortfolioService) GetPortfolio(ctx context.Context, userID int64) (*models.PortfolioResponse, error) {
	portfolio, err := s.repo.GetOrCreatePortfolio(ctx, userID)
	if err != nil {
		return nil, err
	}

	positions, err := s.repo.GetPositions(ctx, userID, nil)
	if err != nil {
		return nil, err
	}

	now := time.Now().UTC().Format(time.RFC3339)
	totalMarketValue := decimal.Zero
	totalRealizedPnl := decimal.Zero
	totalUnrealizedPnl := decimal.Zero

	posResponses := make([]models.PositionResponse, 0, len(positions))
	for _, p := range positions {
		pr, err := s.buildPositionResponse(&p)
		if err != nil {
			return nil, err
		}
		mv, _ := decimal.NewFromString(pr.MarketValue)
		rp, _ := decimal.NewFromString(pr.RealizedPnl)
		up, _ := decimal.NewFromString(pr.UnrealizedPnl)
		totalMarketValue = totalMarketValue.Add(mv)
		totalRealizedPnl = totalRealizedPnl.Add(rp)
		totalUnrealizedPnl = totalUnrealizedPnl.Add(up)
		posResponses = append(posResponses, *pr)
	}

	totalEquity := portfolio.CashBalance.Add(totalMarketValue)
	totalPnl := totalRealizedPnl.Add(totalUnrealizedPnl)

	return &models.PortfolioResponse{
		UserID:           userID,
		CashBalance:      portfolio.CashBalance.StringFixed(2),
		TotalMarketValue: totalMarketValue.StringFixed(2),
		TotalEquity:      totalEquity.StringFixed(2),
		RealizedPnl:      totalRealizedPnl.StringFixed(2),
		UnrealizedPnl:    totalUnrealizedPnl.StringFixed(2),
		TotalPnl:         totalPnl.StringFixed(2),
		Positions:        posResponses,
		UpdatedAt:        now,
	}, nil
}

func (s *PortfolioService) GetPositions(ctx context.Context, userID int64, symbol *string) (*models.PositionListResponse, error) {
	positions, err := s.repo.GetPositions(ctx, userID, symbol)
	if err != nil {
		return nil, err
	}
	items := make([]models.PositionResponse, 0, len(positions))
	for _, p := range positions {
		pr, err := s.buildPositionResponse(&p)
		if err != nil {
			return nil, err
		}
		items = append(items, *pr)
	}
	return &models.PositionListResponse{Items: items}, nil
}

func (s *PortfolioService) GetPositionBySymbol(ctx context.Context, userID int64, symbol string) (*models.PositionResponse, error) {
	pos, err := s.repo.GetPosition(ctx, userID, symbol)
	if err != nil {
		return nil, err
	}
	if pos == nil {
		return nil, nil
	}
	return s.buildPositionResponse(pos)
}

func (s *PortfolioService) GetPnl(ctx context.Context, userID int64) (*models.PortfolioPnlResponse, error) {
	resp, err := s.GetPortfolio(ctx, userID)
	if err != nil {
		return nil, err
	}
	return &models.PortfolioPnlResponse{
		RealizedPnl:   resp.RealizedPnl,
		UnrealizedPnl: resp.UnrealizedPnl,
		TotalPnl:      resp.TotalPnl,
		Currency:      "USD",
		UpdatedAt:     resp.UpdatedAt,
	}, nil
}

func (s *PortfolioService) Deposit(ctx context.Context, userID int64, amount decimal.Decimal) (*models.BalanceResponse, error) {
	if amount.LessThanOrEqual(decimal.Zero) {
		return nil, fmt.Errorf("amount must be positive")
	}
	portfolio, err := s.repo.GetOrCreatePortfolio(ctx, userID)
	if err != nil {
		return nil, err
	}
	newBalance := portfolio.CashBalance.Add(amount)
	p, err := s.repo.UpdateCashBalance(ctx, userID, newBalance)
	if err != nil {
		return nil, err
	}
	return &models.BalanceResponse{
		UserID:    userID,
		Balance:   p.CashBalance.StringFixed(2),
		Currency:  "USD",
		UpdatedAt: p.UpdatedAt.UTC().Format(time.RFC3339),
	}, nil
}

func (s *PortfolioService) Withdraw(ctx context.Context, userID int64, amount decimal.Decimal) (*models.BalanceResponse, error) {
	if amount.LessThanOrEqual(decimal.Zero) {
		return nil, fmt.Errorf("amount must be positive")
	}
	portfolio, err := s.repo.GetOrCreatePortfolio(ctx, userID)
	if err != nil {
		return nil, err
	}
	if portfolio.CashBalance.LessThan(amount) {
		return nil, &InsufficientBalanceError{}
	}
	newBalance := portfolio.CashBalance.Sub(amount)
	p, err := s.repo.UpdateCashBalance(ctx, userID, newBalance)
	if err != nil {
		return nil, err
	}
	return &models.BalanceResponse{
		UserID:    userID,
		Balance:   p.CashBalance.StringFixed(2),
		Currency:  "USD",
		UpdatedAt: p.UpdatedAt.UTC().Format(time.RFC3339),
	}, nil
}

func (s *PortfolioService) ListOrders(ctx context.Context, userID int64, status, symbol *string, limit, offset int) (*models.OrderHistoryListResponse, error) {
	orders, total, err := s.repo.ListOrders(ctx, userID, status, symbol, limit, offset)
	if err != nil {
		return nil, err
	}
	items := make([]models.OrderHistoryItem, 0, len(orders))
	for _, o := range orders {
		items = append(items, models.OrderHistoryItem{
			ID:              o.ID.String(),
			Symbol:          o.Symbol,
			Side:            string(o.Side),
			Quantity:        o.Quantity,
			Status:          string(o.Status),
			AvgFillPrice:    o.AvgFillPrice,
			RejectionReason: o.RejectionReason,
			CreatedAt:       o.CreatedAt.UTC().Format(time.RFC3339),
			UpdatedAt:       o.UpdatedAt.UTC().Format(time.RFC3339),
		})
	}
	return &models.OrderHistoryListResponse{Items: items, Total: total, Limit: limit, Offset: offset}, nil
}

func (s *PortfolioService) ListTrades(ctx context.Context, userID int64, symbol, side *string, limit, offset int) (*models.TradeHistoryListResponse, error) {
	trades, total, err := s.repo.ListTrades(ctx, userID, symbol, side, limit, offset)
	if err != nil {
		return nil, err
	}
	items := make([]models.TradeHistoryItem, 0, len(trades))
	for _, t := range trades {
		items = append(items, models.TradeHistoryItem{
			ID:          t.ID.String(),
			OrderID:     t.OrderID.String(),
			Symbol:      t.Symbol,
			Side:        string(t.Side),
			Quantity:     t.Quantity,
			Price:       t.Price.StringFixed(2),
			GrossAmount: t.GrossAmount.StringFixed(2),
			FeeAmount:   t.FeeAmount,
			ExecutedAt:  t.ExecutedAt.UTC().Format(time.RFC3339),
		})
	}
	return &models.TradeHistoryListResponse{Items: items, Total: total, Limit: limit, Offset: offset}, nil
}

// ProcessTradingEvent handles Kafka events from Trading Service
func (s *PortfolioService) ProcessTradingEvent(ctx context.Context, event *models.TradingEvent) error {
	switch event.EventType {
	case "ORDER_FILLED":
		return s.handleOrderFilled(ctx, event)
	case "ORDER_REJECTED":
		return s.handleOrderRejected(ctx, event)
	case "ORDER_CANCELLED":
		return s.handleOrderCancelled(ctx, event)
	case "TRADE_EXECUTED":
		return s.handleTradeExecuted(ctx, event)
	default:
		return fmt.Errorf("unknown event type: %s", event.EventType)
	}
}

func (s *PortfolioService) handleOrderFilled(ctx context.Context, event *models.TradingEvent) error {
	orderID, _ := uuid.Parse(event.OrderID)
	avgFill := event.AvgFillPrice
	return s.repo.UpdateOrderStatus(ctx, orderID, models.OrderStatusFilled, &avgFill, nil)
}

func (s *PortfolioService) handleOrderRejected(ctx context.Context, event *models.TradingEvent) error {
	orderID, _ := uuid.Parse(event.OrderID)
	reason := event.RejectionReason
	return s.repo.UpdateOrderStatus(ctx, orderID, models.OrderStatusRejected, nil, &reason)
}

func (s *PortfolioService) handleOrderCancelled(ctx context.Context, event *models.TradingEvent) error {
	orderID, _ := uuid.Parse(event.OrderID)
	return s.repo.UpdateOrderStatus(ctx, orderID, models.OrderStatusCancelled, nil, nil)
}

func (s *PortfolioService) handleTradeExecuted(ctx context.Context, event *models.TradingEvent) error {
	tradeID, _ := uuid.Parse(event.TradeID)
	orderID, _ := uuid.Parse(event.OrderID)
	price, _ := decimal.NewFromString(event.Price)
	grossAmount, _ := decimal.NewFromString(event.GrossAmount)

	var feeAmount *string
	if event.FeeAmount != "" {
		feeAmount = &event.FeeAmount
	}

	ts, _ := time.Parse(time.RFC3339, event.Timestamp)

	// Upsert order so it exists in portfolio's order history
	avgFill := event.AvgFillPrice
	now := time.Now()
	_ = s.repo.InsertOrder(ctx, &models.Order{
		ID:           orderID,
		UserID:       event.UserID,
		Symbol:       event.Symbol,
		Side:         models.OrderSide(event.Side),
		Quantity:     event.Quantity,
		Status:       models.OrderStatusFilled,
		AvgFillPrice: &avgFill,
		CreatedAt:    now,
		UpdatedAt:    now,
	})

	trade := &models.Trade{
		ID:          tradeID,
		OrderID:     orderID,
		UserID:      event.UserID,
		Symbol:      event.Symbol,
		Side:        models.OrderSide(event.Side),
		Quantity:    event.Quantity,
		Price:       price,
		GrossAmount: grossAmount,
		FeeAmount:   feeAmount,
		ExecutedAt:  ts,
	}
	if err := s.repo.InsertTrade(ctx, trade); err != nil {
		return err
	}

	// Update position
	pos, err := s.repo.GetPosition(ctx, event.UserID, event.Symbol)
	if err != nil {
		return err
	}

	if event.Side == "BUY" {
		if pos == nil {
			return s.repo.UpsertPosition(ctx, event.UserID, event.Symbol, event.Quantity, price, decimal.Zero)
		}
		totalCost := pos.AvgPrice.Mul(decimal.NewFromInt(int64(pos.Quantity))).Add(price.Mul(decimal.NewFromInt(int64(event.Quantity))))
		newQty := pos.Quantity + event.Quantity
		newAvg := totalCost.Div(decimal.NewFromInt(int64(newQty)))
		return s.repo.UpsertPosition(ctx, event.UserID, event.Symbol, newQty, newAvg, pos.RealizedPnl)
	}

	// SELL
	if pos == nil {
		return fmt.Errorf("cannot sell position that doesn't exist")
	}
	realized := price.Sub(pos.AvgPrice).Mul(decimal.NewFromInt(int64(event.Quantity)))
	newQty := pos.Quantity - event.Quantity
	newRealized := pos.RealizedPnl.Add(realized)
	if newQty <= 0 {
		newQty = 0
	}
	return s.repo.UpsertPosition(ctx, event.UserID, event.Symbol, newQty, pos.AvgPrice, newRealized)
}

func (s *PortfolioService) buildPositionResponse(p *models.Position) (*models.PositionResponse, error) {
	currentPrice, err := s.price.GetCurrentPrice(p.Symbol)
	if err != nil {
		return nil, err
	}
	marketValue := currentPrice.Mul(decimal.NewFromInt(int64(p.Quantity)))
	unrealized := currentPrice.Sub(p.AvgPrice).Mul(decimal.NewFromInt(int64(p.Quantity)))
	totalPnl := p.RealizedPnl.Add(unrealized)

	return &models.PositionResponse{
		Symbol:        p.Symbol,
		Quantity:      p.Quantity,
		AvgPrice:      p.AvgPrice.StringFixed(2),
		CurrentPrice:  currentPrice.StringFixed(2),
		MarketValue:   marketValue.StringFixed(2),
		RealizedPnl:   p.RealizedPnl.StringFixed(2),
		UnrealizedPnl: unrealized.StringFixed(2),
		TotalPnl:      totalPnl.StringFixed(2),
		Currency:      "USD",
		UpdatedAt:     p.UpdatedAt.UTC().Format(time.RFC3339),
	}, nil
}

// InsufficientBalanceError is returned when withdrawal exceeds balance
type InsufficientBalanceError struct{}

func (e *InsufficientBalanceError) Error() string {
	return "insufficient balance"
}
