package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

const (
	defaultHTTPPort            = 8080
	defaultHTTPReadTimeout     = 5 * time.Second
	defaultHTTPWriteTimeout    = 10 * time.Second
	defaultHTTPShutdownTimeout = 10 * time.Second
	defaultQueryTimeout        = 5 * time.Second
	defaultBasePath            = "/api/v1"
	defaultClickHouseURL       = "http://localhost:8123"
	defaultClickHouseDatabase  = "default"
	defaultOrderBookSampleSize = 500
)

type Config struct {
	ServiceName         string
	HTTPPort            int
	BasePath            string
	ReadTimeout         time.Duration
	WriteTimeout        time.Duration
	ShutdownTimeout     time.Duration
	QueryTimeout        time.Duration
	OrderBookSampleSize int
	ClickHouseURL       string
	ClickHouseDatabase  string
	ClickHouseUser      string
	ClickHousePassword  string
}

func Load() (Config, error) {
	cfg := Config{
		ServiceName:         envOrDefault("SERVICE_NAME", "market-data-service"),
		HTTPPort:            envInt("HTTP_PORT", defaultHTTPPort),
		BasePath:            normalizeBasePath(envOrDefault("HTTP_BASE_PATH", defaultBasePath)),
		ReadTimeout:         envDuration("HTTP_READ_TIMEOUT", defaultHTTPReadTimeout),
		WriteTimeout:        envDuration("HTTP_WRITE_TIMEOUT", defaultHTTPWriteTimeout),
		ShutdownTimeout:     envDuration("HTTP_SHUTDOWN_TIMEOUT", defaultHTTPShutdownTimeout),
		QueryTimeout:        envDuration("CLICKHOUSE_QUERY_TIMEOUT", defaultQueryTimeout),
		OrderBookSampleSize: envInt("ORDER_BOOK_SAMPLE_SIZE", defaultOrderBookSampleSize),
		ClickHouseURL:       strings.TrimRight(envOrDefault("CLICKHOUSE_URL", defaultClickHouseURL), "/"),
		ClickHouseDatabase:  envOrDefault("CLICKHOUSE_DATABASE", defaultClickHouseDatabase),
		ClickHouseUser:      os.Getenv("CLICKHOUSE_USER"),
		ClickHousePassword:  os.Getenv("CLICKHOUSE_PASSWORD"),
	}

	if cfg.HTTPPort <= 0 {
		return Config{}, fmt.Errorf("HTTP_PORT must be greater than zero")
	}
	if cfg.OrderBookSampleSize <= 0 {
		return Config{}, fmt.Errorf("ORDER_BOOK_SAMPLE_SIZE must be greater than zero")
	}
	if cfg.ClickHouseURL == "" {
		return Config{}, fmt.Errorf("CLICKHOUSE_URL must not be empty")
	}
	if cfg.ClickHouseDatabase == "" {
		return Config{}, fmt.Errorf("CLICKHOUSE_DATABASE must not be empty")
	}

	return cfg, nil
}

func (c Config) Address() string {
	return fmt.Sprintf(":%d", c.HTTPPort)
}

func envOrDefault(key, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(key)); value != "" {
		return value
	}
	return fallback
}

func envInt(key string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}

	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func envDuration(key string, fallback time.Duration) time.Duration {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}

	parsed, err := time.ParseDuration(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func normalizeBasePath(path string) string {
	path = strings.TrimSpace(path)
	if path == "" || path == "/" {
		return ""
	}
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	return strings.TrimRight(path, "/")
}
