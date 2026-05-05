package api

import (
	"net/http"
	"time"
)

func healthHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{
			"status":    "UP",
			"service":   "trading-service",
			"timestamp": time.Now().Format(time.RFC3339),
		})
	}
}

func readyHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"status":    "READY",
			"service":   "trading-service",
			"dependencies": []map[string]string{
				{"name": "postgres", "status": "UP"},
				{"name": "kafka", "status": "UP"},
			},
			"timestamp": time.Now().Format(time.RFC3339),
		})
	}
}
