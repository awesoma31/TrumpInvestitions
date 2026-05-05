package external

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/shopspring/decimal"
)

type PortfolioClient interface {
	GetCashBalance(ctx context.Context, userID int64) (decimal.Decimal, error)
	GetAssetQuantity(ctx context.Context, userID int64, symbol string) (int, error)
}

// PortfolioHTTPClient calls real portfolio-service endpoints
type PortfolioHTTPClient struct {
	baseURL    string
	httpClient *http.Client
}

func NewPortfolioHTTPClient(baseURL string) *PortfolioHTTPClient {
	return &PortfolioHTTPClient{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 5 * time.Second,
			Transport: &http.Transport{
				MaxIdleConns:        20,
				MaxIdleConnsPerHost: 20,
				IdleConnTimeout:     90 * time.Second,
			},
		},
	}
}

type cashBalanceResp struct {
	UserID   int64  `json:"userId"`
	Balance  string `json:"balance"`
	Currency string `json:"currency"`
}

type assetQuantityResp struct {
	UserID   int64  `json:"userId"`
	Symbol   string `json:"symbol"`
	Quantity int    `json:"quantity"`
}

func (c *PortfolioHTTPClient) GetCashBalance(ctx context.Context, userID int64) (decimal.Decimal, error) {
	req, err := http.NewRequestWithContext(ctx, "GET", fmt.Sprintf("%s/api/v1/balance/cash", c.baseURL), nil)
	if err != nil {
		return decimal.Zero, err
	}
	req.Header.Set("X-User-Id", fmt.Sprintf("%d", userID))

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return decimal.Zero, fmt.Errorf("portfolio-service request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return decimal.Zero, fmt.Errorf("portfolio-service returned status %d", resp.StatusCode)
	}

	var body cashBalanceResp
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return decimal.Zero, fmt.Errorf("failed to decode portfolio response: %w", err)
	}

	return decimal.NewFromString(body.Balance)
}

func (c *PortfolioHTTPClient) GetAssetQuantity(ctx context.Context, userID int64, symbol string) (int, error) {
	req, err := http.NewRequestWithContext(ctx, "GET", fmt.Sprintf("%s/api/v1/assets/%s/quantity", c.baseURL, symbol), nil)
	if err != nil {
		return 0, err
	}
	req.Header.Set("X-User-Id", fmt.Sprintf("%d", userID))

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return 0, fmt.Errorf("portfolio-service request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return 0, fmt.Errorf("portfolio-service returned status %d", resp.StatusCode)
	}

	var body assetQuantityResp
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return 0, fmt.Errorf("failed to decode portfolio response: %w", err)
	}

	return body.Quantity, nil
}
