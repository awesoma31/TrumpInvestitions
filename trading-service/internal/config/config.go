package config

import "os"

type Config struct {
	DatabaseURL  string
	KafkaBrokers []string
	KafkaTopic   string
	ServerPort   string
}

func Load() *Config {
	return &Config{
		DatabaseURL:  getEnv("DATABASE_URL", "postgres://trading:trading@localhost:5432/trading?sslmode=disable"),
		KafkaBrokers: []string{getEnv("KAFKA_BROKER", "localhost:9092")},
		KafkaTopic:   getEnv("KAFKA_TOPIC", "trading-events"),
		ServerPort:   getEnv("SERVER_PORT", "8080"),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
