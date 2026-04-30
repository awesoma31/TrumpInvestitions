#!/usr/bin/env bash
set -euo pipefail

PORTFOLIO_HEALTH="${PORTFOLIO_URL:-http://localhost:8081}/api/v1/system/health"
TRADING_HEALTH="${TRADING_URL:-http://localhost:8082}/api/v1/system/health"
TIMEOUT="${WAIT_TIMEOUT:-60}"

wait_url() {
    local url="$1"
    local deadline=$((SECONDS + TIMEOUT))
    echo "  Waiting for $url ..."
    while [[ $SECONDS -lt $deadline ]]; do
        if curl -sf "$url" >/dev/null 2>&1; then
            echo "  OK: $url"
            return 0
        fi
        sleep 2
    done
    echo "  ERROR: $url did not become ready within ${TIMEOUT}s" >&2
    return 1
}

echo "=== Waiting for services (timeout: ${TIMEOUT}s) ==="
wait_url "$PORTFOLIO_HEALTH"
wait_url "$TRADING_HEALTH"
echo "=== All services ready ==="
