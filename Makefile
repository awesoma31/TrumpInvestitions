PORTFOLIO_URL ?= http://localhost:8081/api/v1
TRADING_URL   ?= http://localhost:8082/api/v1
KAFKA_WAIT    ?= 3

.PHONY: test-unit test-portfolio test-integration test-all up down wait

## Юнит-тесты (не требуют запущенной инфраструктуры)
test-unit:
	cd trading-service  && go test ./...
	cd portfolio-service && go test ./...

## Интеграционные тесты только portfolio-service
test-portfolio:
	pip install -q -r tests/requirements.txt
	python tests/test_portfolio.py --base-url $(PORTFOLIO_URL)

## Интеграционные тесты portfolio + trading (e2e)
test-integration:
	pip install -q -r tests/requirements.txt
	python tests/test_portfolio_trading.py \
		--portfolio-url $(PORTFOLIO_URL) \
		--trading-url   $(TRADING_URL) \
		--kafka-wait    $(KAFKA_WAIT)

## Поднять инфраструктуру
up:
	docker compose up -d --build

## Остановить инфраструктуру
down:
	docker compose down

## Дождаться готовности сервисов (с таймаутом)
wait:
	@bash scripts/wait-services.sh

## Полный цикл: поднять → подождать → юнит + интеграция
test-all: up wait test-unit test-integration
