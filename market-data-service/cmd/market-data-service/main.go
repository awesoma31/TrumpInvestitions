package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/awesoma/trumpinvestitions/market-data-service/internal/config"
	"github.com/awesoma/trumpinvestitions/market-data-service/internal/httpapi"
	"github.com/awesoma/trumpinvestitions/market-data-service/internal/service"
	"github.com/awesoma/trumpinvestitions/market-data-service/internal/storage/clickhouse"
)

func main() {
	logger := log.New(os.Stdout, "[market-data-service] ", log.LstdFlags|log.Lmsgprefix)

	cfg, err := config.Load()
	if err != nil {
		logger.Fatalf("load config: %v", err)
	}

	httpClient := &http.Client{Timeout: cfg.QueryTimeout}
	repo := clickhouse.New(
		cfg.ClickHouseURL,
		cfg.ClickHouseDatabase,
		cfg.ClickHouseUser,
		cfg.ClickHousePassword,
		httpClient,
		cfg.QueryTimeout,
		cfg.OrderBookSampleSize,
	)
	marketDataService := service.New(repo)
	server := httpapi.New(cfg, marketDataService, logger)

	httpServer := &http.Server{
		Addr:         cfg.Address(),
		Handler:      server.Handler(),
		ReadTimeout:  cfg.ReadTimeout,
		WriteTimeout: cfg.WriteTimeout,
	}

	go func() {
		logger.Printf("listening on %s%s", cfg.Address(), cfg.BasePath)
		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Fatalf("http server failed: %v", err)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop

	ctx, cancel := context.WithTimeout(context.Background(), cfg.ShutdownTimeout)
	defer cancel()

	logger.Printf("shutting down")
	if err := httpServer.Shutdown(ctx); err != nil {
		logger.Fatalf("shutdown: %v", err)
	}
	time.Sleep(50 * time.Millisecond)
}
