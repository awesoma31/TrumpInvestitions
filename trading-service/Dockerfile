FROM golang:1.25-alpine AS builder
WORKDIR /app
COPY . .
RUN go mod tidy
RUN go mod download
RUN CGO_ENABLED=0 go build -o trading-service ./cmd/server

FROM alpine:3.19
RUN apk add --no-cache ca-certificates postgresql-client
WORKDIR /app
COPY --from=builder /app/trading-service .
COPY migrations/001_create_orders_table.up.sql /migrations/001_init.sql
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/entrypoint.sh"]