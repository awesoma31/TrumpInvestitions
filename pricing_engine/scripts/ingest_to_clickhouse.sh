#!/usr/bin/env bash
set -euo pipefail

DEVICE="${DEVICE:-/dev/pricing_engine}"
CLICKHOUSE_URL="${CLICKHOUSE_URL:-http://localhost:8123}"
CLICKHOUSE_USER="${CLICKHOUSE_USER:-market_data}"
CLICKHOUSE_PASSWORD="${CLICKHOUSE_PASSWORD:-market_data_password}"
TABLE="${TABLE:-quotes}"
BATCH_SIZE="${BATCH_SIZE:-200}"
SLEEP_SECONDS="${SLEEP_SECONDS:-1}"

TMP_FILE=$(mktemp)

cleanup() {
  rm -f "$TMP_FILE"
}
trap cleanup EXIT

echo "Starting ingestion from $DEVICE → $TABLE"

while true; do
  # читаем батч
  if ! head -n "$BATCH_SIZE" "$DEVICE" >"$TMP_FILE"; then
    echo "Read error from device"
    sleep 1
    continue
  fi

  # отправляем в ClickHouse
  if ! curl -sS -f \
    "${CLICKHOUSE_URL}/?user=${CLICKHOUSE_USER}&password=${CLICKHOUSE_PASSWORD}&query=INSERT%20INTO%20${TABLE}%20FORMAT%20JSONEachRow" \
    --data-binary @"$TMP_FILE"; then
    echo "ClickHouse insert failed, retrying..."
    sleep 1
    continue
  fi

  sleep "$SLEEP_SECONDS"
done
