PORTFOLIO_URL ?= http://localhost:8082/api/v1
TRADING_URL   ?= http://localhost:8083/api/v1
GATEWAY_URL   ?= http://localhost:8080/api/v1
KAFKA_WAIT    ?= 3

.PHONY: setup up down wait \
        db-init clickhouse-init load-data reset-data \
        test-unit test-portfolio test-integration test-gateway test-all

# ─── Запуск ──────────────────────────────────────────────────────────────────

## Первый запуск: поднять всё, инициализировать БД и загрузить данные
setup: up db-init clickhouse-init load-data

## Поднять инфраструктуру (с пересборкой образов)
up:
	docker compose up -d --build

## Остановить инфраструктуру (тома сохраняются)
down:
	docker compose down

## Остановить и удалить тома (полный сброс)
down-clean:
	docker compose down -v

## Дождаться готовности всех сервисов
wait:
	@bash scripts/wait-services.sh

## Дождаться конкретного сервиса: make wait-portfolio
wait-%:
	@bash scripts/wait-services.sh $*

# ─── Инициализация ────────────────────────────────────────────────────────────

## Создать пользователей и БД в postgres (идемпотентно)
db-init:
	@bash scripts/init-postgres.sh
	@echo "Restarting auth-gateway..."
	docker compose restart auth-gateway

## Создать схему таблицы quotes в ClickHouse (идемпотентно)
clickhouse-init:
	db/clickhouse/init_clickhouse.sh

## Собрать pricing_engine и загрузить все сценарии в ClickHouse
load-data:
	make -C pricing_engine
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/btcusdt_1000.yaml | ./pricing_engine/push_input_to_db.sh
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/aapl_1000.yaml   | ./pricing_engine/push_input_to_db.sh
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/ethusdt_1000.yaml | ./pricing_engine/push_input_to_db.sh
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/msft_1000.yaml   | ./pricing_engine/push_input_to_db.sh
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/tsla_1000.yaml   | ./pricing_engine/push_input_to_db.sh

## Сбросить котировки и загрузить заново
reset-data:
	scripts/reset-clickhouse.sh
	$(MAKE) load-data

# ─── Тесты ───────────────────────────────────────────────────────────────────

## Юнит-тесты Go (не требуют запущенной инфраструктуры)
test-unit:
	cd trading-service  && go test ./...
	cd portfolio-service && go test ./...

## Интеграционные тесты portfolio-service
test-portfolio: wait-portfolio-service
	python portfolio-service/test_endpoints.py --base-url $(PORTFOLIO_URL)

## E2E-тесты: portfolio + trading + Kafka
test-integration: wait-portfolio-service wait-trading-service
	python tests/test_portfolio_trading.py \
		--portfolio-url $(PORTFOLIO_URL) \
		--trading-url   $(TRADING_URL) \
		--kafka-wait    $(KAFKA_WAIT)

## E2E-тесты через auth-gateway (JWT, portfolio, trading, Kafka)
test-gateway: wait-auth-gateway wait-portfolio-service wait-trading-service
	python tests/test_gateway.py \
		--base-url    $(GATEWAY_URL) \
		--kafka-wait  $(KAFKA_WAIT)

## Полный цикл: поднять → инициализировать → юнит + интеграция + gateway
test-all: up db-init clickhouse-init load-data wait test-unit test-integration test-gateway
