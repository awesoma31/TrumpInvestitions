#!/usr/bin/env bash
set -euo pipefail

tr -d '\r' | curl -s --fail \
  "http://localhost:8123/?user=market_data&password=market_data_password&query=INSERT%20INTO%20quotes%20FORMAT%20JSONEachRow" \
  --data-binary @-
