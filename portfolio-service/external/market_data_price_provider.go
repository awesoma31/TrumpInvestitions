package external

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"time"

	"github.com/shopspring/decimal"
)

type MarketDataPriceProvider struct {
	baseURL    string
	httpClient *http.Client
}

func NewMarketDataPriceProvider(baseURL string) *MarketDataPriceProvider {
	return &MarketDataPriceProvider{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 5 * time.Second,
		},
	}
}

type quoteResponse struct {
	Symbol string `json:"symbol"`
	Last   string `json:"last"`
}

func (p *MarketDataPriceProvider) GetCurrentPrice(symbol string) (decimal.Decimal, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, fmt.Sprintf("%s/quotes/%s", p.baseURL, url.PathEscape(symbol)), nil)
	if err != nil {
		return decimal.Zero, err
	}

	resp, err := p.httpClient.Do(req)
	if err != nil {
		return decimal.Zero, fmt.Errorf("market-data-service request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return decimal.Zero, fmt.Errorf("market-data-service returned status %d", resp.StatusCode)
	}

	var body quoteResponse
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return decimal.Zero, fmt.Errorf("failed to decode market data response: %w", err)
	}

	price, err := decimal.NewFromString(body.Last)
	if err != nil {
		return decimal.Zero, fmt.Errorf("invalid last price %q from market-data-service: %w", body.Last, err)
	}

	return price, nil
}
