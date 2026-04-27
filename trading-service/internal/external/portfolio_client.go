package external

import (
	"context"

	"github.com/shopspring/decimal"
)

type PortfolioClient interface {
	GetCashBalance(ctx context.Context, userID int64) (decimal.Decimal, error)
	GetAssetQuantity(ctx context.Context, userID int64, symbol string) (int, error)
}

type PortfolioClientMock struct{}

func NewPortfolioClientMock() *PortfolioClientMock {
	return &PortfolioClientMock{}
}

func (p *PortfolioClientMock) GetCashBalance(ctx context.Context, userID int64) (decimal.Decimal, error) {
	return decimal.NewFromInt(10_000_000), nil
}

func (p *PortfolioClientMock) GetAssetQuantity(ctx context.Context, userID int64, symbol string) (int, error) {
	return 10000, nil
}
