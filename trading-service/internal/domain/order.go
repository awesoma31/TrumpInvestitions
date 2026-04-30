package domain

import (
	"time"

	"github.com/google/uuid"
)

type OrderSide string

const (
	OrderSideBuy  OrderSide = "BUY"
	OrderSideSell OrderSide = "SELL"
)

type OrderType string

const (
	OrderTypeMarket OrderType = "MARKET"
)

type OrderStatus string

const (
	OrderStatusNew       OrderStatus = "NEW"
	OrderStatusFilled    OrderStatus = "FILLED"
	OrderStatusRejected  OrderStatus = "REJECTED"
	OrderStatusCancelled OrderStatus = "CANCELLED"
)

// OrderRecord – единая таблица для заявок и сделок.
type OrderRecord struct {
	ID              uuid.UUID   `db:"id"`
	UserID          int64       `db:"user_id"`
	Symbol          string      `db:"symbol"`
	Side            OrderSide   `db:"side"`
	OrderType       OrderType   `db:"order_type"`
	Quantity        int         `db:"quantity"`
	Status          OrderStatus `db:"status"`
	FilledQuantity  *int        `db:"filled_quantity"`
	AvgFillPrice    *string     `db:"avg_fill_price"` // decimal в строке
	RejectionReason *string     `db:"rejection_reason"`
	CreatedAt       time.Time   `db:"created_at"`
	UpdatedAt       time.Time   `db:"updated_at"`
	FilledAt        *time.Time  `db:"filled_at"`
	CancelledAt     *time.Time  `db:"cancelled_at"`

	// Поля сделки (заполняются при статусе FILLED)
	TradeID          *uuid.UUID `db:"trade_id"`
	TradePrice       *string    `db:"trade_price"`
	TradeGrossAmount *string    `db:"trade_gross_amount"`
	TradeFeeAmount   *string    `db:"trade_fee_amount"`
	TradeExecutedAt  *time.Time `db:"trade_executed_at"`
}

// События Kafka (из models.go)
type TradingEvent struct {
	EventType       string `json:"eventType"`
	UserID          int64  `json:"userId"`
	OrderID         string `json:"orderId"`
	Symbol          string `json:"symbol"`
	Side            string `json:"side"`
	Quantity        int    `json:"quantity"`
	Price           string `json:"price,omitempty"`
	AvgFillPrice    string `json:"avgFillPrice,omitempty"`
	GrossAmount     string `json:"grossAmount,omitempty"`
	FeeAmount       string `json:"feeAmount,omitempty"`
	RejectionReason string `json:"rejectionReason,omitempty"`
	TradeID         string `json:"tradeId,omitempty"`
	Timestamp       string `json:"timestamp"`
}
