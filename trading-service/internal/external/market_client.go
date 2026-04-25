package external

import (
	"context"

	"github.com/shopspring/decimal"
)

type MarketDataClient interface {
	GetMarketData(ctx context.Context, symbol string) (price decimal.Decimal, volume int, err error)
}

type MarketClientMock struct{}

func NewMarketClientMock() *MarketClientMock {
	return &MarketClientMock{}
}

func (m *MarketClientMock) GetMarketData(ctx context.Context, symbol string) (decimal.Decimal, int, error) {
	return decimal.Zero, 1000000, nil
}
