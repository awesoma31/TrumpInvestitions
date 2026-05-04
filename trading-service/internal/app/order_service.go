package app

import (
	"context"
	"errors"
	"fmt"
	"log"
	"strconv"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"

	"github.com/vnikolaenko/trading-service/internal/domain"
	"github.com/vnikolaenko/trading-service/internal/external"
	"github.com/vnikolaenko/trading-service/internal/repository"
	"github.com/vnikolaenko/trading-service/internal/telemetry"
	"go.opentelemetry.io/otel/attribute"
)

var (
	ErrInsufficientFunds        = errors.New("INSUFFICIENT_FUNDS")
	ErrInsufficientAssets       = errors.New("INSUFFICIENT_ASSETS")
	ErrInsufficientMarketVolume = errors.New("INSUFFICIENT_MARKET_VOLUME")
	ErrOrderNotFound            = errors.New("NOT_FOUND")
	ErrOrderNotCancellable      = errors.New("ORDER_NOT_CANCELLABLE")
)

type CreateOrderReq struct {
	UserID   int64
	Symbol   string
	Side     domain.OrderSide
	Type     domain.OrderType
	Quantity int
}

type OrderService struct {
	repo            repository.OrderRepository
	marketClient    external.MarketDataClient
	portfolioClient external.PortfolioClient
	kafkaProducer   external.TradingEventProducer
}

func NewOrderService(
	repo repository.OrderRepository,
	marketClient external.MarketDataClient,
	portfolioClient external.PortfolioClient,
	kafkaProducer external.TradingEventProducer,
) *OrderService {
	return &OrderService{
		repo:            repo,
		marketClient:    marketClient,
		portfolioClient: portfolioClient,
		kafkaProducer:   kafkaProducer,
	}
}

func (s *OrderService) CreateOrder(ctx context.Context, req CreateOrderReq) (*domain.OrderRecord, error) {
	ctx, span := telemetry.Tracer().Start(ctx, "OrderService.CreateOrder")
	defer span.End()
	span.SetAttributes(
		attribute.Int64("user.id", req.UserID),
		attribute.String("symbol", req.Symbol),
		attribute.String("side", string(req.Side)),
		attribute.Int("quantity", req.Quantity),
	)

	order := &domain.OrderRecord{
		ID:        uuid.New(),
		UserID:    req.UserID,
		Symbol:    req.Symbol,
		Side:      req.Side,
		OrderType: domain.OrderTypeMarket,
		Quantity:  req.Quantity,
		Status:    domain.OrderStatusNew,
	}
	userIDStr := strconv.FormatInt(req.UserID, 10)

	if err := s.repo.CreateOrder(ctx, order); err != nil {
		return nil, fmt.Errorf("create order: %w", err)
	}

	// Fetch market data and portfolio data concurrently.
	type marketResult struct {
		price  decimal.Decimal
		volume int
		err    error
	}
	type portfolioResult struct {
		balance     decimal.Decimal
		positionQty int
		err         error
	}

	marketCh := make(chan marketResult, 1)
	portfolioCh := make(chan portfolioResult, 1)

	var wg sync.WaitGroup
	wg.Add(2)

	go func() {
		defer wg.Done()
		p, v, e := s.marketClient.GetMarketData(ctx, req.Symbol, req.Side)
		marketCh <- marketResult{price: p, volume: v, err: e}
	}()

	go func() {
		defer wg.Done()
		var res portfolioResult
		switch req.Side {
		case domain.OrderSideBuy:
			res.balance, res.err = s.portfolioClient.GetCashBalance(ctx, req.UserID)
		case domain.OrderSideSell:
			res.positionQty, res.err = s.portfolioClient.GetAssetQuantity(ctx, req.UserID, req.Symbol)
		}
		portfolioCh <- res
	}()

	wg.Wait()
	close(marketCh)
	close(portfolioCh)

	mr := <-marketCh
	if mr.err != nil {
		return nil, fmt.Errorf("get market data: %w", mr.err)
	}
	price, volume := mr.price, mr.volume

	if volume < req.Quantity {
		reason := "INSUFFICIENT_MARKET_VOLUME"
		_ = s.repo.UpdateOrderStatus(ctx, order.ID.String(), userIDStr, domain.OrderStatusRejected, &reason)
		order.Status = domain.OrderStatusRejected
		order.RejectionReason = &reason
		go s.publishEvent(ctx, order, "")
		return order, ErrInsufficientMarketVolume
	}

	grossAmount := price.Mul(decimal.NewFromInt(int64(req.Quantity)))

	pr := <-portfolioCh
	if pr.err != nil {
		switch req.Side {
		case domain.OrderSideBuy:
			return nil, fmt.Errorf("get balance: %w", pr.err)
		case domain.OrderSideSell:
			return nil, fmt.Errorf("get position: %w", pr.err)
		}
	}

	switch req.Side {
	case domain.OrderSideBuy:
		if pr.balance.LessThan(grossAmount) {
			reason := "INSUFFICIENT_FUNDS"
			_ = s.repo.UpdateOrderStatus(ctx, order.ID.String(), userIDStr, domain.OrderStatusRejected, &reason)
			order.Status = domain.OrderStatusRejected
			order.RejectionReason = &reason
			go s.publishEvent(ctx, order, "")
			return order, ErrInsufficientFunds
		}
	case domain.OrderSideSell:
		if pr.positionQty < req.Quantity {
			reason := "INSUFFICIENT_ASSETS"
			_ = s.repo.UpdateOrderStatus(ctx, order.ID.String(), userIDStr, domain.OrderStatusRejected, &reason)
			order.Status = domain.OrderStatusRejected
			order.RejectionReason = &reason
			go s.publishEvent(ctx, order, "")
			return order, ErrInsufficientAssets
		}
	}

	tradeID := uuid.New()
	executedAt := time.Now()
	priceStr := price.String()
	grossStr := grossAmount.String()
	if err := s.repo.FillOrder(ctx, order.ID.String(), tradeID.String(), priceStr, grossStr, executedAt); err != nil {
		return nil, fmt.Errorf("fill order: %w", err)
	}
	order.Status = domain.OrderStatusFilled
	order.TradeID = &tradeID
	order.TradePrice = &priceStr
	order.TradeGrossAmount = &grossStr
	order.TradeExecutedAt = &executedAt
	filledQty := req.Quantity
	order.FilledQuantity = &filledQty
	order.AvgFillPrice = &priceStr
	order.FilledAt = &executedAt

	go s.publishEvent(ctx, order, tradeID.String())
	return order, nil
}

func (s *OrderService) CancelOrder(ctx context.Context, orderID, userID string) (*domain.OrderRecord, error) {
	order, err := s.repo.GetOrderByID(ctx, orderID, userID)
	if err != nil {
		return nil, err
	}
	if order == nil {
		return nil, ErrOrderNotFound
	}
	if order.Status != domain.OrderStatusNew {
		return nil, ErrOrderNotCancellable
	}
	if err := s.repo.CancelOrder(ctx, orderID, userID); err != nil {
		return nil, err
	}
	order.Status = domain.OrderStatusCancelled
	now := time.Now()
	order.CancelledAt = &now
	go s.publishEvent(ctx, order, "")
	return order, nil
}

func (s *OrderService) GetOrder(ctx context.Context, orderID, userID string) (*domain.OrderRecord, error) {
	return s.repo.GetOrderByID(ctx, orderID, userID)
}

func (s *OrderService) ListOrders(ctx context.Context, userID int64, filters repository.OrderFilter) ([]domain.OrderRecord, int, error) {
	return s.repo.ListOrders(ctx, userID, filters)
}

func (s *OrderService) GetTrade(ctx context.Context, tradeID, userID string) (*domain.OrderRecord, error) {
	return s.repo.GetTradeByID(ctx, tradeID, userID)
}

func (s *OrderService) ListTrades(ctx context.Context, userID int64, filters repository.TradeFilter) ([]domain.OrderRecord, int, error) {
	return s.repo.ListTrades(ctx, userID, filters)
}

func (s *OrderService) publishEvent(ctx context.Context, order *domain.OrderRecord, tradeID string) {
	eventType := mapEventType(order.Status)
	if eventType == "" {
		return
	}
	ev := &domain.TradingEvent{
		EventType: eventType,
		UserID:    order.UserID,
		OrderID:   order.ID.String(),
		Symbol:    order.Symbol,
		Side:      string(order.Side),
		Quantity:  order.Quantity,
		Timestamp: time.Now().Format(time.RFC3339),
	}
	if order.Status == domain.OrderStatusFilled {
		ev.Price = *order.TradePrice
		ev.AvgFillPrice = *order.AvgFillPrice
		ev.GrossAmount = *order.TradeGrossAmount
		if order.TradeFeeAmount != nil {
			ev.FeeAmount = *order.TradeFeeAmount
		}
		ev.TradeID = tradeID
	}
	if order.RejectionReason != nil {
		ev.RejectionReason = *order.RejectionReason
	}
	if err := s.kafkaProducer.ProduceTradingEvent(context.Background(), ev); err != nil {
		log.Printf("ERROR publishing kafka event %s: %v", ev.EventType, err)
	} else {
		log.Printf("Published kafka event: %s for order %s", ev.EventType, ev.OrderID)
	}

	if order.Status == domain.OrderStatusFilled {
		tradeEv := *ev
		tradeEv.EventType = "TRADE_EXECUTED"
		if err := s.kafkaProducer.ProduceTradingEvent(context.Background(), &tradeEv); err != nil {
			log.Printf("ERROR publishing kafka TRADE_EXECUTED: %v", err)
		} else {
			log.Printf("Published kafka event: TRADE_EXECUTED for order %s", ev.OrderID)
		}
	}
}

func mapEventType(status domain.OrderStatus) string {
	switch status {
	case domain.OrderStatusFilled:
		return "ORDER_FILLED"
	case domain.OrderStatusRejected:
		return "ORDER_REJECTED"
	case domain.OrderStatusCancelled:
		return "ORDER_CANCELLED"
	default:
		return ""
	}
}
