package main

import (
	"context"
	"database/sql"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/awesoma31/portfolio-service/config"
	"github.com/awesoma31/portfolio-service/external"
	"github.com/awesoma31/portfolio-service/handler"
	"github.com/awesoma31/portfolio-service/kafka"
	"github.com/awesoma31/portfolio-service/repository"
	"github.com/awesoma31/portfolio-service/service"
	"github.com/awesoma31/portfolio-service/telemetry"
	"github.com/gorilla/mux"
	_ "github.com/lib/pq"
	"go.opentelemetry.io/contrib/instrumentation/github.com/gorilla/mux/otelmux"
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

	db, err := sql.Open("postgres", cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("failed to connect to database: %v", err)
	}
	defer db.Close()

	db.SetMaxOpenConns(25)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(5 * time.Minute)

	if err := db.Ping(); err != nil {
		log.Fatalf("database ping failed: %v", err)
	}
	log.Println("Connected to PostgreSQL")

	repo := repository.NewPostgresRepository(db)
	priceProvider := external.NewMarketDataPriceProvider(cfg.MarketDataURL)
	svc := service.NewPortfolioService(repo, priceProvider)

	r := mux.NewRouter()
	r.Use(otelmux.Middleware("portfolio-service"))
	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
			log.Printf("[http] %s %s from %s", req.Method, req.URL.Path, req.RemoteAddr)
			next.ServeHTTP(w, req)
		})
	})
	h := handler.NewHandler(svc, repo)
	h.RegisterRoutes(r)

	// Kafka consumer
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	consumer := kafka.NewConsumer(cfg.KafkaBrokers, cfg.KafkaTopic, cfg.KafkaGroupID, svc)
	go consumer.Start(ctx)

	srv := &http.Server{
		Addr:         ":" + cfg.HTTPPort,
		Handler:      r,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		log.Printf("Portfolio service listening on :%s", cfg.HTTPPort)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("server error: %v", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("Shutting down...")

	cancel()
	consumer.Close()

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer shutdownCancel()
	srv.Shutdown(shutdownCtx)
}
