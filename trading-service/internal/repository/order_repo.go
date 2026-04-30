package repository

import (
	"context"
	"time"

	"github.com/vnikolaenko/trading-service/internal/domain"
)

type OrderRepository interface {
	CreateOrder(ctx context.Context, order *domain.OrderRecord) error
	GetOrderByID(ctx context.Context, orderID, userID string) (*domain.OrderRecord, error)
	ListOrders(ctx context.Context, userID int64, filters OrderFilter) ([]domain.OrderRecord, int, error)
	UpdateOrderStatus(ctx context.Context, orderID, userID string, status domain.OrderStatus, reason *string) error
	FillOrder(ctx context.Context, orderID string, tradeID string, price, grossAmount string, executedAt time.Time) error
	CancelOrder(ctx context.Context, orderID, userID string) error
	GetTradeByID(ctx context.Context, tradeID, userID string) (*domain.OrderRecord, error)
	ListTrades(ctx context.Context, userID int64, filters TradeFilter) ([]domain.OrderRecord, int, error)
}

type OrderFilter struct {
	Status *domain.OrderStatus
	Symbol *string
	Side   *domain.OrderSide
	Limit  int
	Offset int
}

type TradeFilter struct {
	Symbol *string
	Side   *domain.OrderSide
	Limit  int
	Offset int
}
