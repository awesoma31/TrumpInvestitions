package config

import "os"

type Config struct {
	HTTPPort      string
	DatabaseURL   string
	MarketDataURL string
	KafkaBrokers  string
	KafkaTopic    string
	KafkaGroupID  string
	OtelEndpoint  string
}

func Load() *Config {
	return &Config{
		HTTPPort:      getEnv("HTTP_PORT", "8080"),
		DatabaseURL:   getEnv("DATABASE_URL", "postgres://postgres:postgres@localhost:5432/portfolio?sslmode=disable"),
		MarketDataURL: getEnv("MARKET_DATA_URL", "http://market-data-service:8081/api/v1"),
		KafkaBrokers:  getEnv("KAFKA_BROKERS", "localhost:9092"),
		KafkaTopic:    getEnv("KAFKA_TOPIC", "trading-events"),
		KafkaGroupID:  getEnv("KAFKA_GROUP_ID", "portfolio-service"),
		OtelEndpoint:  getEnv("OTEL_EXPORTER_ENDPOINT", ""),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
