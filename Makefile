PORTFOLIO_URL ?= http://localhost:8082/api/v1
TRADING_URL   ?= http://localhost:8083/api/v1
GATEWAY_URL   ?= http://localhost:8080/api/v1
KAFKA_WAIT    ?= 3

.PHONY: setup up down wait status telemetry \
        db-init clickhouse-init load-data reset-data \
        load-test load-test-10000 load-status \
        test-unit test-portfolio test-integration test-gateway test-all

# --- Status ------------------------------------------------------------------

## Show status of all services
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
		[ -n "$$ip" ] && echo "  Backend IP: $$ip:8080" || echo "  Could not define IP -- use ipconfig/ip addr"'

# --- Observability -----------------------------------------------------------

## Open Jaeger UI (distributed tracing) in browser
telemetry:
	@echo "Jaeger UI: http://localhost:16686"
	@bash -c 'if command -v xdg-open >/dev/null 2>&1; then xdg-open http://localhost:16686; \
		elif command -v open >/dev/null 2>&1; then open http://localhost:16686; \
		else echo "Open manually: http://localhost:16686"; fi'

# --- Launch ------------------------------------------------------------------

## First run: bring up everything, init DB and load data
setup: up db-init clickhouse-init load-data

## Start infrastructure (with image rebuild)
up:
	docker compose up -d --build

## Stop infrastructure (volumes are preserved)
down:
	docker compose down

## Stop and remove volumes (full reset)
down-clean:
	docker compose down -v

## Wait for all services to become ready
wait:
	@bash scripts/wait-services.sh

## Wait for a specific service: make wait-portfolio
wait-%:
	@bash scripts/wait-services.sh $*

# --- Init --------------------------------------------------------------------

## Create users and DBs in postgres (idempotent)
db-init:
	@bash scripts/init-postgres.sh
	@echo "Restarting auth-gateway..."
	docker compose restart auth-gateway

## Create quotes table schema in ClickHouse (idempotent)
clickhouse-init:
	bash db/clickhouse/init_clickhouse.sh

## Build pricing_engine and load all scenarios into ClickHouse
load-data:
	make -C pricing_engine
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/btcusdt_1000.yaml | bash pricing_engine/push_input_to_db.sh
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/aapl_1000.yaml   | bash pricing_engine/push_input_to_db.sh
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/ethusdt_1000.yaml | bash pricing_engine/push_input_to_db.sh
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/msft_1000.yaml   | bash pricing_engine/push_input_to_db.sh
	./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/tsla_1000.yaml   | bash pricing_engine/push_input_to_db.sh

## Reset quotes and reload
reset-data:
	bash scripts/reset-clickhouse.sh
	$(MAKE) load-data

# --- Load testing -------------------------------------------------------------

## Run Kotlin load-service with configurable load: make load-test LOAD_USERS=1000
load-test:
	LOAD_USERS=$(or $(LOAD_USERS),1000) \
	LOAD_DURATION_SECONDS=$(or $(LOAD_DURATION_SECONDS),120) \
	LOAD_RAMP_UP_SECONDS=$(or $(LOAD_RAMP_UP_SECONDS),30) \
	LOAD_WITHDRAW_REQUEST_PERCENT=$(or $(LOAD_WITHDRAW_REQUEST_PERCENT),5) \
	LOAD_SYSTEM_REQUEST_PERCENT=$(or $(LOAD_SYSTEM_REQUEST_PERCENT),10) \
	LOAD_HISTORY_WINDOW_SECONDS=$(or $(LOAD_HISTORY_WINDOW_SECONDS),86400) \
	LOAD_HISTORY_LIMIT=$(or $(LOAD_HISTORY_LIMIT),100) \
	LOAD_THINK_TIME_MS=$(or $(LOAD_THINK_TIME_MS),250) \
	docker compose --profile load up --build load-service

## Run Kotlin load-service with 10000 virtual clients
load-test-10000:
	LOAD_USERS=10000 \
	LOAD_DURATION_SECONDS=$(or $(LOAD_DURATION_SECONDS),300) \
	LOAD_RAMP_UP_SECONDS=$(or $(LOAD_RAMP_UP_SECONDS),120) \
	LOAD_WITHDRAW_REQUEST_PERCENT=$(or $(LOAD_WITHDRAW_REQUEST_PERCENT),5) \
	LOAD_SYSTEM_REQUEST_PERCENT=$(or $(LOAD_SYSTEM_REQUEST_PERCENT),10) \
	LOAD_HISTORY_WINDOW_SECONDS=$(or $(LOAD_HISTORY_WINDOW_SECONDS),86400) \
	LOAD_HISTORY_LIMIT=$(or $(LOAD_HISTORY_LIMIT),100) \
	LOAD_THINK_TIME_MS=$(or $(LOAD_THINK_TIME_MS),250) \
	docker compose --profile load up --build load-service

## Show current load-service status
load-status:
	curl -s http://localhost:8095/api/v1/load/status

# --- Tests -------------------------------------------------------------------

## Go unit tests (no running infrastructure required)
test-unit:
	cd trading-service  && go test ./...
	cd portfolio-service && go test ./...

## Integration tests for portfolio-service
test-portfolio: wait-portfolio-service
	python portfolio-service/test_endpoints.py --base-url $(PORTFOLIO_URL)

## E2E tests: portfolio + trading + Kafka
test-integration: wait-portfolio-service wait-trading-service
	python tests/test_portfolio_trading.py \
		--portfolio-url $(PORTFOLIO_URL) \
		--trading-url   $(TRADING_URL) \
		--kafka-wait    $(KAFKA_WAIT)

## E2E tests via auth-gateway (JWT, portfolio, trading, Kafka)
test-gateway: wait-auth-gateway wait-portfolio-service wait-trading-service
	python tests/test_gateway.py \
		--base-url    $(GATEWAY_URL) \
		--kafka-wait  $(KAFKA_WAIT)

## Full cycle: up -> init -> unit + integration + gateway
test-all: up db-init clickhouse-init load-data wait test-unit test-integration test-gateway
