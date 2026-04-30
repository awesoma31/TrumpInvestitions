FROM golang:1.25.5-alpine AS builder

WORKDIR /app

COPY go.mod ./
COPY cmd ./cmd
COPY internal ./internal

RUN CGO_ENABLED=0 GOOS=linux go build -o /bin/market-data-service ./cmd/market-data-service

FROM alpine:3.22

WORKDIR /app

COPY --from=builder /bin/market-data-service /usr/local/bin/market-data-service

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/market-data-service"]
