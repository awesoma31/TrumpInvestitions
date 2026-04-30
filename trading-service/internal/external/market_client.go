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
	prices := map[string]float64{
		"AAPL": 190.0,
		"GOOG": 140.0,
		"TSLA": 250.0,
		"MSFT": 420.0,
	}
	p, ok := prices[symbol]
	if !ok {
		p = 100.0
	}
	return decimal.NewFromFloat(p), 1000000, nil
}
