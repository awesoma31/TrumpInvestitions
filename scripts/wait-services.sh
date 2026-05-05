#!/usr/bin/env bash
set -euo pipefail

TIMEOUT=${TIMEOUT:-60}

wait_for() {
  local name=$1 url=$2
  local elapsed=0
  echo "Waiting for $name ($url)..."
  until curl -sf --connect-timeout 2 --max-time 3 "$url" > /dev/null 2>&1; do
    if [ "$elapsed" -ge "$TIMEOUT" ]; then
      echo "ERROR: $name did not become ready within ${TIMEOUT}s" >&2
      exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo "  $name is ready"
}

# Если аргументы переданы — ждём только их, иначе все сервисы
if [ $# -gt 0 ]; then
  case "$1" in
    auth-gateway)        wait_for "auth-gateway"        "http://localhost:8080/api/v1/system/health" ;;
    market-data-service) wait_for "market-data-service" "http://localhost:8081/api/v1/system/health" ;;
    portfolio-service)   wait_for "portfolio-service"   "http://localhost:8082/api/v1/system/health" ;;
    trading-service)     wait_for "trading-service"     "http://localhost:8083/api/v1/system/health" ;;
    *) echo "Unknown service: $1" >&2; exit 1 ;;
  esac
else
  wait_for "auth-gateway"        "http://localhost:8080/api/v1/system/health"
  wait_for "market-data-service" "http://localhost:8081/api/v1/system/health"
  wait_for "portfolio-service"   "http://localhost:8082/api/v1/system/health"
  wait_for "trading-service"     "http://localhost:8083/api/v1/system/health"
fi
