package external

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"time"

	"github.com/shopspring/decimal"

	"github.com/vnikolaenko/trading-service/internal/domain"
)

type MarketDataClient interface {
	GetMarketData(ctx context.Context, symbol string, side domain.OrderSide) (price decimal.Decimal, volume int, err error)
}

type MarketDataHTTPClient struct {
	baseURL    string
	httpClient *http.Client
}

func NewMarketDataHTTPClient(baseURL string) *MarketDataHTTPClient {
	return &MarketDataHTTPClient{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 5 * time.Second,
		},
	}
}

type orderBookResp struct {
	Symbol  string               `json:"symbol"`
	Bids    []orderBookLevelResp `json:"bids"`
	Asks    []orderBookLevelResp `json:"asks"`
	BestBid *string              `json:"bestBid"`
	BestAsk *string              `json:"bestAsk"`
}

type orderBookLevelResp struct {
	Price    string `json:"price"`
	Quantity int64  `json:"quantity"`
}

func (c *MarketDataHTTPClient) GetMarketData(ctx context.Context, symbol string, side domain.OrderSide) (decimal.Decimal, int, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, fmt.Sprintf("%s/order-book/%s?depth=20", c.baseURL, url.PathEscape(symbol)), nil)
	if err != nil {
		return decimal.Zero, 0, err
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return decimal.Zero, 0, fmt.Errorf("market-data-service request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return decimal.Zero, 0, fmt.Errorf("market-data-service returned status %d", resp.StatusCode)
	}

	var body orderBookResp
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return decimal.Zero, 0, fmt.Errorf("failed to decode market data response: %w", err)
	}

	var priceStr *string
	var levels []orderBookLevelResp
	switch side {
	case domain.OrderSideBuy:
		priceStr = body.BestAsk
		levels = body.Asks
	case domain.OrderSideSell:
		priceStr = body.BestBid
		levels = body.Bids
	default:
		return decimal.Zero, 0, fmt.Errorf("unsupported order side: %s", side)
	}

	if priceStr == nil {
		return decimal.Zero, 0, fmt.Errorf("market-data-service returned empty %s price for %s", side, symbol)
	}

	price, err := decimal.NewFromString(*priceStr)
	if err != nil {
		return decimal.Zero, 0, fmt.Errorf("invalid price %q from market-data-service: %w", *priceStr, err)
	}

	var totalVolume int64
	for _, level := range levels {
		totalVolume += level.Quantity
	}
	if totalVolume > int64(^uint(0)>>1) {
		return decimal.Zero, 0, fmt.Errorf("market volume overflow for %s", symbol)
	}

	return price, int(totalVolume), nil
}
