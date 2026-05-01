package external

import (
	"context"
	"io"
	"net/http"
	"strings"
	"testing"

	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/vnikolaenko/trading-service/internal/domain"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return f(req)
}

func TestMarketDataHTTPClientGetMarketDataBuy(t *testing.T) {
	client := NewMarketDataHTTPClient("http://market-data.test")
	client.httpClient = &http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			assert.Equal(t, "/order-book/AAPL", req.URL.Path)
			assert.Equal(t, "depth=20", req.URL.RawQuery)
			return &http.Response{
				StatusCode: http.StatusOK,
				Header:     make(http.Header),
				Body: io.NopCloser(strings.NewReader(`{
					"symbol":"AAPL",
					"bids":[{"price":"189.9","quantity":3}],
					"asks":[{"price":"190.1","quantity":4},{"price":"190.2","quantity":6}],
					"bestBid":"189.9",
					"bestAsk":"190.1",
					"timestamp":"2026-05-01T00:00:00Z"
				}`)),
			}, nil
		}),
	}

	price, volume, err := client.GetMarketData(context.Background(), "AAPL", domain.OrderSideBuy)

	require.NoError(t, err)
	assert.True(t, price.Equal(decimal.RequireFromString("190.1")))
	assert.Equal(t, 10, volume)
}

func TestMarketDataHTTPClientGetMarketDataSell(t *testing.T) {
	client := NewMarketDataHTTPClient("http://market-data.test")
	client.httpClient = &http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			return &http.Response{
				StatusCode: http.StatusOK,
				Header:     make(http.Header),
				Body: io.NopCloser(strings.NewReader(`{
					"symbol":"AAPL",
					"bids":[{"price":"189.9","quantity":3},{"price":"189.8","quantity":5}],
					"asks":[{"price":"190.1","quantity":4}],
					"bestBid":"189.9",
					"bestAsk":"190.1",
					"timestamp":"2026-05-01T00:00:00Z"
				}`)),
			}, nil
		}),
	}

	price, volume, err := client.GetMarketData(context.Background(), "AAPL", domain.OrderSideSell)

	require.NoError(t, err)
	assert.True(t, price.Equal(decimal.RequireFromString("189.9")))
	assert.Equal(t, 8, volume)
}
