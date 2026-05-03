package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"

	"github.com/vnikolaenko/trading-service/internal/api"
	"github.com/vnikolaenko/trading-service/internal/app"
	"github.com/vnikolaenko/trading-service/internal/config"
	"github.com/vnikolaenko/trading-service/internal/external"
	"github.com/vnikolaenko/trading-service/internal/repository"
	"github.com/vnikolaenko/trading-service/internal/telemetry"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
)

func main() {
	cfg := config.Load()

	// OpenTelemetry
	shutdownTracer, err := telemetry.InitTracer(context.Background(), cfg.OtelEndpoint)
	if err != nil {
		log.Fatalf("failed to init tracer: %v", err)
	}
	defer func() {
		if err := shutdownTracer(context.Background()); err != nil {
			log.Printf("error shutting down tracer: %v", err)
		}
	}()

	// Database
	db, err := sqlx.Connect("postgres", cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("failed to connect to database: %v", err)
	}
	defer db.Close()

	// Kafka producer
	kafkaProducer, err := external.NewKafkaProducer(cfg.KafkaBrokers, cfg.KafkaTopic)
	if err != nil {
		log.Fatalf("failed to create kafka producer: %v", err)
	}
	defer kafkaProducer.Close()

	// External clients
	marketClient := external.NewMarketDataHTTPClient(cfg.MarketDataURL)
	portfolioClient := external.NewPortfolioHTTPClient(cfg.PortfolioURL)

	// Repository
	orderRepo := repository.NewPostgresOrderRepo(db)

	// Application service
	orderService := app.NewOrderService(orderRepo, marketClient, portfolioClient, kafkaProducer)

	// Router
	r := chi.NewRouter()
	r.Use(api.CORSMiddleware)
	api.RegisterRoutes(r, orderService)

	// Server
	srv := &http.Server{
		Addr:    ":" + cfg.ServerPort,
		Handler: otelhttp.NewHandler(r, "trading-service"),
	}

	go func() {
		log.Printf("starting server on port %s", cfg.ServerPort)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("listen: %v", err)
		}
	}()

	// Graceful shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("shutting down...")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		log.Fatalf("forced shutdown: %v", err)
	}
	log.Println("server stopped")
}
