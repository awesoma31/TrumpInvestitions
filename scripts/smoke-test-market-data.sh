#!/usr/bin/env bash
set -euo pipefail

SCENARIO_PATH="${1:-pricing_engine/examples/basic_btc.yaml}"
API_BASE_URL="${API_BASE_URL:-http://localhost:8080/api/v1}"
RESET_DB="${RESET_DB:-1}"

if [[ ! -f "$SCENARIO_PATH" ]]; then
  echo "Scenario file not found: $SCENARIO_PATH" >&2
  exit 1
fi

symbol="$(awk -F': *' '$1=="symbol"{print $2; exit}' "$SCENARIO_PATH" | tr -d '"' | xargs)"
if [[ -z "$symbol" ]]; then
  echo "Could not read symbol from scenario: $SCENARIO_PATH" >&2
  exit 1
fi

pretty_print() {
  if command -v jq >/dev/null 2>&1; then
    jq .
  else
    cat
  fi
}

print_section() {
  printf '\n== %s ==\n' "$1"
}

request() {
  local label="$1"
  local url="$2"
  local response
  local status
  local body

  print_section "$label"

  response="$(curl -sS -w '\n%{http_code}' "$url")"
  status="$(printf '%s\n' "$response" | tail -n1)"
  body="$(printf '%s\n' "$response" | sed '$d')"

  printf 'GET %s\n' "$url"
  printf 'HTTP %s\n' "$status"
  printf '%s\n' "$body" | pretty_print
}

wait_until_ready() {
  local attempt=0
  local response
  local status
  local body

  while (( attempt < 30 )); do
    response="$(curl -sS -w '\n%{http_code}' "$API_BASE_URL/system/ready" || true)"
    status="$(printf '%s\n' "$response" | tail -n1)"
    body="$(printf '%s\n' "$response" | sed '$d')"

    if [[ "$status" == "200" ]]; then
      print_section "Readiness"
      printf '%s\n' "$body" | pretty_print
      return 0
    fi

    attempt=$((attempt + 1))
    sleep 1
  done

  echo "market-data-service did not become ready in time" >&2
  return 1
}

print_section "Start services"
docker compose up -d --build clickhouse market-data-service

print_section "Init ClickHouse schema"
./db/clickhouse/init_clickhouse.sh

if [[ "$RESET_DB" == "1" ]]; then
  print_section "Reset quotes table"
  docker exec clickhouse-local clickhouse-client --query="TRUNCATE TABLE quotes"
fi

print_section "Build pricing_engine"
make -C pricing_engine

print_section "Load quotes from scenario"
./pricing_engine/pricing_engine --scenario "$SCENARIO_PATH" | ./pricing_engine/push_input_to_db.sh

wait_until_ready

request "Health" "$API_BASE_URL/system/health"
request "Search instruments" "$API_BASE_URL/instruments?q=$symbol"
request "Instrument by symbol" "$API_BASE_URL/instruments/$symbol"
request "Quotes list" "$API_BASE_URL/quotes?symbols=$symbol"
request "Quote by symbol" "$API_BASE_URL/quotes/$symbol"
request "Candle history" "$API_BASE_URL/history/candles?symbol=$symbol&from=2020-01-01T00:00:00Z&to=2035-01-01T00:00:00Z&interval=1m&limit=100"
request "Order book" "$API_BASE_URL/order-book/$symbol?depth=20"

print_section "Done"
printf 'Smoke test finished for symbol %s\n' "$symbol"
