package config

import "os"

type Config struct {
	HTTPPort     string
	DatabaseURL  string
	KafkaBrokers string
	KafkaTopic   string
	KafkaGroupID string
}

func Load() *Config {
	return &Config{
		HTTPPort:     getEnv("HTTP_PORT", "8080"),
		DatabaseURL:  getEnv("DATABASE_URL", "postgres://postgres:postgres@localhost:5432/portfolio?sslmode=disable"),
		KafkaBrokers: getEnv("KAFKA_BROKERS", "localhost:9092"),
		KafkaTopic:   getEnv("KAFKA_TOPIC", "trading-events"),
		KafkaGroupID: getEnv("KAFKA_GROUP_ID", "portfolio-service"),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
