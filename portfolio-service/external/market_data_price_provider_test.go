package external

import (
	"io"
	"net/http"
	"strings"
	"testing"

	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return f(req)
}

func TestMarketDataPriceProviderGetCurrentPrice(t *testing.T) {
	provider := NewMarketDataPriceProvider("http://market-data.test")
	provider.httpClient = &http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			assert.Equal(t, "/quotes/AAPL", req.URL.Path)
			return &http.Response{
				StatusCode: http.StatusOK,
				Header:     make(http.Header),
				Body: io.NopCloser(strings.NewReader(`{
					"symbol":"AAPL",
					"bid":"189.9",
					"ask":"190.1",
					"last":"190.0",
					"timestamp":"2026-05-01T00:00:00Z"
				}`)),
			}, nil
		}),
	}

	price, err := provider.GetCurrentPrice("AAPL")

	require.NoError(t, err)
	assert.True(t, price.Equal(decimal.RequireFromString("190.0")))
}
