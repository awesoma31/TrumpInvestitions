package models

import (
	"time"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
)

type OrderSide string

const (
	OrderSideBuy  OrderSide = "BUY"
	OrderSideSell OrderSide = "SELL"
)

type OrderStatus string

const (
	OrderStatusNew       OrderStatus = "NEW"
	OrderStatusFilled    OrderStatus = "FILLED"
	OrderStatusRejected  OrderStatus = "REJECTED"
	OrderStatusCancelled OrderStatus = "CANCELLED"
)

// --- DB entities ---

type Portfolio struct {
	UserID      int64           `json:"userId"`
	CashBalance decimal.Decimal `json:"cashBalance"`
	UpdatedAt   time.Time       `json:"updatedAt"`
}

type Position struct {
	ID          int64           `json:"-"`
	UserID      int64           `json:"-"`
	Symbol      string          `json:"symbol"`
	Quantity    int             `json:"quantity"`
	AvgPrice    decimal.Decimal `json:"avgPrice"`
	RealizedPnl decimal.Decimal `json:"realizedPnl"`
	UpdatedAt   time.Time       `json:"updatedAt"`
}

type Order struct {
	ID              uuid.UUID       `json:"id"`
	UserID          int64           `json:"-"`
	Symbol          string          `json:"symbol"`
	Side            OrderSide       `json:"side"`
	Quantity        int             `json:"quantity"`
	Status          OrderStatus     `json:"status"`
	AvgFillPrice    *string         `json:"avgFillPrice"`
	RejectionReason *string         `json:"rejectionReason"`
	CreatedAt       time.Time       `json:"createdAt"`
	UpdatedAt       time.Time       `json:"updatedAt"`
}

type Trade struct {
	ID          uuid.UUID       `json:"id"`
	OrderID     uuid.UUID       `json:"orderId"`
	UserID      int64           `json:"-"`
	Symbol      string          `json:"symbol"`
	Side        OrderSide       `json:"side"`
	Quantity    int             `json:"quantity"`
	Price       decimal.Decimal `json:"price"`
	GrossAmount decimal.Decimal `json:"grossAmount"`
	FeeAmount   *string         `json:"feeAmount"`
	ExecutedAt  time.Time       `json:"executedAt"`
}

// --- API responses ---

type PositionResponse struct {
	Symbol        string `json:"symbol"`
	Quantity      int    `json:"quantity"`
	AvgPrice      string `json:"avgPrice"`
	CurrentPrice  string `json:"currentPrice"`
	MarketValue   string `json:"marketValue"`
	RealizedPnl   string `json:"realizedPnl"`
	UnrealizedPnl string `json:"unrealizedPnl"`
	TotalPnl      string `json:"totalPnl"`
	Currency      string `json:"currency"`
	UpdatedAt     string `json:"updatedAt"`
}

type PositionListResponse struct {
	Items []PositionResponse `json:"items"`
}

type PortfolioResponse struct {
	UserID           int64              `json:"userId"`
	CashBalance      string             `json:"cashBalance"`
	TotalMarketValue string             `json:"totalMarketValue"`
	TotalEquity      string             `json:"totalEquity"`
	RealizedPnl      string             `json:"realizedPnl"`
	UnrealizedPnl    string             `json:"unrealizedPnl"`
	TotalPnl         string             `json:"totalPnl"`
	Positions        []PositionResponse `json:"positions"`
	UpdatedAt        string             `json:"updatedAt"`
}

type PortfolioPnlResponse struct {
	RealizedPnl   string `json:"realizedPnl"`
	UnrealizedPnl string `json:"unrealizedPnl"`
	TotalPnl      string `json:"totalPnl"`
	Currency      string `json:"currency"`
	UpdatedAt     string `json:"updatedAt"`
}

type BalanceOperationRequest struct {
	Amount string `json:"amount"`
}

type BalanceResponse struct {
	UserID    int64  `json:"userId"`
	Balance   string `json:"balance"`
	Currency  string `json:"currency"`
	UpdatedAt string `json:"updatedAt"`
}

type OrderHistoryItem struct {
	ID              string  `json:"id"`
	Symbol          string  `json:"symbol"`
	Side            string  `json:"side"`
	Quantity        int     `json:"quantity"`
	Status          string  `json:"status"`
	AvgFillPrice    *string `json:"avgFillPrice"`
	RejectionReason *string `json:"rejectionReason"`
	CreatedAt       string  `json:"createdAt"`
	UpdatedAt       string  `json:"updatedAt"`
}

type OrderHistoryListResponse struct {
	Items  []OrderHistoryItem `json:"items"`
	Total  int                `json:"total"`
	Limit  int                `json:"limit"`
	Offset int                `json:"offset"`
}

type TradeHistoryItem struct {
	ID          string  `json:"id"`
	OrderID     string  `json:"orderId"`
	Symbol      string  `json:"symbol"`
	Side        string  `json:"side"`
	Quantity    int     `json:"quantity"`
	Price       string  `json:"price"`
	GrossAmount string  `json:"grossAmount"`
	FeeAmount   *string `json:"feeAmount"`
	ExecutedAt  string  `json:"executedAt"`
}

type TradeHistoryListResponse struct {
	Items  []TradeHistoryItem `json:"items"`
	Total  int                `json:"total"`
	Limit  int                `json:"limit"`
	Offset int                `json:"offset"`
}

type HealthResponse struct {
	Status    string `json:"status"`
	Service   string `json:"service"`
	Timestamp string `json:"timestamp"`
}

type DependencyStatus struct {
	Name   string `json:"name"`
	Status string `json:"status"`
}

type ReadinessResponse struct {
	Status       string             `json:"status"`
	Service      string             `json:"service"`
	Dependencies []DependencyStatus `json:"dependencies"`
	Timestamp    string             `json:"timestamp"`
}

type ErrorResponse struct {
	Code    string        `json:"code"`
	Message string        `json:"message"`
	Details []ErrorDetail `json:"details,omitempty"`
	TraceID string        `json:"traceId"`
}

type ErrorDetail struct {
	Field string `json:"field"`
	Issue string `json:"issue"`
}

// --- Kafka event ---

type TradingEvent struct {
	EventType       string `json:"eventType"` // ORDER_FILLED, ORDER_REJECTED, ORDER_CANCELLED, TRADE_EXECUTED
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
