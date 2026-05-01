#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-clickhouse-local}"
TABLE_NAME="${TABLE_NAME:-quotes}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required but not found in PATH" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  echo "ClickHouse container is not running: $CONTAINER_NAME" >&2
  echo "Start it first with: docker compose up -d clickhouse" >&2
  exit 1
fi

echo "Truncating ClickHouse table '$TABLE_NAME' in container '$CONTAINER_NAME'..."
docker exec "$CONTAINER_NAME" clickhouse-client --query="TRUNCATE TABLE $TABLE_NAME"
echo "Done."
