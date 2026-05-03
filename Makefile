PORTFOLIO_URL ?= http://localhost:8082/api/v1
TRADING_URL   ?= http://localhost:8083/api/v1
GATEWAY_URL   ?= http://localhost:8080/api/v1
KAFKA_WAIT    ?= 3

.PHONY: setup up down wait status \
        db-init clickhouse-init load-data reset-data \
        test-unit test-portfolio test-integration test-gateway test-all

# ─── Статус ──────────────────────────────────────────────────────────────────

## Показать состояние всех сервисов
status:
	@echo "=== Docker containers ==="
	@docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
	@echo ""
	@echo "=== Health checks ==="
	@bash -c 'check() { url=$$1; name=$$2; r=$$(curl -sf --max-time 3 "$$url" 2>/dev/null) && echo "  [OK]  $$name" || echo "  [--]  $$name"; }; \
		check http://localhost:8080/api/v1/system/health "auth-gateway    :8080"; \
		check http://localhost:8081/api/v1/system/health "market-data     :8081"; \
		check http://localhost:8082/api/v1/system/health "portfolio       :8082"; \
		check http://localhost:8083/api/v1/system/health "trading         :8083"; \
		check "http://localhost:8123/?user=market_data&password=market_data_password&query=SELECT%201" \
		      "clickhouse      :8123"; \
		check http://localhost:8090                      "swagger-ui      :8090"'
	@echo ""
	@echo "=== Quotes in ClickHouse ==="
	@bash -c 'n=$$(curl -sf --max-time 3 "http://localhost:8123/?user=market_data&password=market_data_password&query=SELECT%20count()%20FROM%20quotes" 2>/dev/null); \
		[ -n "$$n" ] && echo "  quotes: $$n rows" || echo "  quotes: unavailable"'
	@echo ""
	@echo "=== IP for mobile app ==="
	@bash -c '\
		if command -v ip >/dev/null 2>&1; then \
			ip=$(ip route get 1 2>/dev/null | grep -oP "src \K[\d.]+"); \
		elif command -v ipconfig >/dev/null 2>&1; then \
			ip=$$(ipconfig 2>/dev/null | grep -A4 "Wi-Fi\|Wireless" | grep "IPv4" | grep -oP "[\d]+\.[\d]+\.[\d]+\.[\d]+" | head -1); \
		fi; \
		[ -n "$$ip" ] && echo "  Backend IP: $$ip:8080" || echo "  Could not define IP — use ipconfig/ip addr"'

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
	bash db/clickhouse/init_clickhouse.sh

## Собрать pricing_engine и загрузить все сценарии в ClickHouse
load-data:
	make -C pricing_engine
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/btcusdt_1000.yaml | bash pricing_engine/push_input_to_db.sh
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/aapl_1000.yaml   | bash pricing_engine/push_input_to_db.sh
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/ethusdt_1000.yaml | bash pricing_engine/push_input_to_db.sh
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/msft_1000.yaml   | bash pricing_engine/push_input_to_db.sh
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/tsla_1000.yaml   | bash pricing_engine/push_input_to_db.sh

## Сбросить котировки и загрузить заново
reset-data:
	bash scripts/reset-clickhouse.sh
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
