#!/usr/bin/env bash
set -euo pipefail

docker exec -i clickhouse-local clickhouse-client \
  --query="INSERT INTO quotes FORMAT JSONEachRow"
