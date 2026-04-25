package api

import (
	"context"
	"net/http"
	"strconv"
)

type contextKey string

const UserIDKey contextKey = "userID"

// UserIDMiddleware извлекает X-User-Id и помещает его в контекст запроса.
func UserIDMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		uidStr := r.Header.Get("X-User-Id")
		if uidStr == "" {
			http.Error(w, `{"code":"VALIDATION_ERROR","message":"missing X-User-Id header"}`, http.StatusBadRequest)
			return
		}
		uid, err := strconv.ParseInt(uidStr, 10, 64)
		if err != nil {
			http.Error(w, `{"code":"VALIDATION_ERROR","message":"invalid X-User-Id"}`, http.StatusBadRequest)
			return
		}
		ctx := context.WithValue(r.Context(), UserIDKey, uid)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// getUserID достаёт значение из контекста.
func getUserID(r *http.Request) int64 {
	return r.Context().Value(UserIDKey).(int64)
}

// CORSMiddleware добавляет заголовки CORS.
func CORSMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, X-User-Id")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusOK)
			return
		}
		next.ServeHTTP(w, r)
	})
}
