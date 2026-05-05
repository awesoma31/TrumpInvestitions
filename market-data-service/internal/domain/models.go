package domain

import (
	"strconv"
	"strings"
	"time"
)

type Instrument struct {
	Symbol   string `json:"symbol"`
	Name     string `json:"name"`
	Currency string `json:"currency"`
	LotSize  int    `json:"lotSize"`
	Active   bool   `json:"active"`
}

type InstrumentListResponse struct {
	Items  []Instrument `json:"items"`
	Total  int          `json:"total"`
	Limit  int          `json:"limit"`
	Offset int          `json:"offset"`
}

type Quote struct {
	Symbol    string  `json:"symbol"`
	Bid       string  `json:"bid"`
	Ask       string  `json:"ask"`
	Last      string  `json:"last"`
	Open      *string `json:"open,omitempty"`
	High      *string `json:"high,omitempty"`
	Low       *string `json:"low,omitempty"`
	Close     *string `json:"close,omitempty"`
	Volume    *int64  `json:"volume,omitempty"`
	Timestamp string  `json:"timestamp"`
}

type QuoteListResponse struct {
	Items []Quote `json:"items"`
}

type Candle struct {
	Timestamp string `json:"timestamp"`
	Open      string `json:"open"`
	High      string `json:"high"`
	Low       string `json:"low"`
	Close     string `json:"close"`
	Volume    int64  `json:"volume"`
}

type CandleListResponse struct {
	Symbol   string   `json:"symbol"`
	Interval string   `json:"interval"`
	Items    []Candle `json:"items"`
}

type OrderBookLevel struct {
	Price    string `json:"price"`
	Quantity int64  `json:"quantity"`
}

type OrderBookResponse struct {
	Symbol    string           `json:"symbol"`
	Bids      []OrderBookLevel `json:"bids"`
	Asks      []OrderBookLevel `json:"asks"`
	BestBid   *string          `json:"bestBid"`
	BestAsk   *string          `json:"bestAsk"`
	Spread    *string          `json:"spread,omitempty"`
	Timestamp string           `json:"timestamp"`
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

type QuoteSnapshot struct {
	Symbol      string
	Bid         float64
	Ask         float64
	Last        float64
	Open        *float64
	High        *float64
	Low         *float64
	Close       *float64
	Volume      *int64
	EventTimeNS uint64
}

type CandlePoint struct {
	TimestampNS uint64
	Open        float64
	High        float64
	Low         float64
	Close       float64
	Volume      int64
}

type OrderBookLevelSnapshot struct {
	Price    float64
	Quantity int64
}

func NewInstrument(symbol string) Instrument {
	return Instrument{
		Symbol:   symbol,
		Name:     inferInstrumentName(symbol),
		Currency: inferCurrency(symbol),
		LotSize:  1,
		Active:   true,
	}
}

func NewQuote(snapshot QuoteSnapshot) Quote {
	quote := Quote{
		Symbol:    snapshot.Symbol,
		Bid:       formatPrice(snapshot.Bid),
		Ask:       formatPrice(snapshot.Ask),
		Last:      formatPrice(snapshot.Last),
		Timestamp: nsToTime(snapshot.EventTimeNS).Format(time.RFC3339Nano),
	}

	if snapshot.Open != nil {
		value := formatPrice(*snapshot.Open)
		quote.Open = &value
	}
	if snapshot.High != nil {
		value := formatPrice(*snapshot.High)
		quote.High = &value
	}
	if snapshot.Low != nil {
		value := formatPrice(*snapshot.Low)
		quote.Low = &value
	}
	if snapshot.Close != nil {
		value := formatPrice(*snapshot.Close)
		quote.Close = &value
	}
	if snapshot.Volume != nil {
		volume := *snapshot.Volume
		quote.Volume = &volume
	}

	return quote
}

func NewCandle(timestampNS uint64, open, high, low, close float64, volume int64) Candle {
	return Candle{
		Timestamp: nsToTime(timestampNS).Format(time.RFC3339Nano),
		Open:      formatPrice(open),
		High:      formatPrice(high),
		Low:       formatPrice(low),
		Close:     formatPrice(close),
		Volume:    volume,
	}
}

func NewOrderBookLevel(price float64, quantity int64) OrderBookLevel {
	return OrderBookLevel{
		Price:    formatPrice(price),
		Quantity: quantity,
	}
}

func FormatSpread(bid, ask float64) *string {
	if ask <= 0 || bid <= 0 || ask < bid {
		return nil
	}
	value := formatPrice(ask - bid)
	return &value
}

func formatPrice(value float64) string {
	raw := strconv.FormatFloat(value, 'f', -1, 64)
	if strings.Contains(raw, ".") {
		raw = strings.TrimRight(raw, "0")
		raw = strings.TrimRight(raw, ".")
	}
	if raw == "" || raw == "-0" {
		return "0"
	}
	return raw
}

func nsToTime(ns uint64) time.Time {
	seconds := int64(ns / 1_000_000_000)
	nanos := int64(ns % 1_000_000_000)
	return time.Unix(seconds, nanos).UTC()
}

func inferInstrumentName(symbol string) string {
	if name, ok := instrumentNameOverrides[strings.ToUpper(strings.TrimSpace(symbol))]; ok {
		return name
	}

	switch {
	case strings.HasSuffix(symbol, "USDT"):
		return strings.TrimSuffix(symbol, "USDT") + " / Tether"
	case strings.HasSuffix(symbol, "USD"):
		return strings.TrimSuffix(symbol, "USD") + " / US Dollar"
	case strings.HasSuffix(symbol, "EUR"):
		return strings.TrimSuffix(symbol, "EUR") + " / Euro"
	case strings.HasSuffix(symbol, "RUB"):
		return strings.TrimSuffix(symbol, "RUB") + " / Russian Ruble"
	default:
		return symbol
	}
}

func inferCurrency(symbol string) string {
	switch {
	case strings.HasSuffix(symbol, "USDT"):
		return "USDT"
	case strings.HasSuffix(symbol, "USD"):
		return "USD"
	case strings.HasSuffix(symbol, "EUR"):
		return "EUR"
	case strings.HasSuffix(symbol, "RUB"):
		return "RUB"
	default:
		return "USD"
	}
}

var instrumentNameOverrides = map[string]string{
	"AAPL":    "Apple Inc.",
	"AMZN":    "Amazon.com Inc.",
	"BTCUSDT": "Bitcoin / Tether",
	"ETHUSDT": "Ethereum / Tether",
	"GOOG":    "Alphabet Inc.",
	"GOOGL":   "Alphabet Inc.",
	"MSFT":    "Microsoft Corp.",
	"TSLA":    "Tesla Inc.",
}
