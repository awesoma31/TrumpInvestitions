#!/usr/bin/env bash
set -euo pipefail

SCENARIOS_DIR="${1:-pricing_engine/examples/generated}"
ENGINE_BIN="./pricing_engine/pricing_engine"
PUSH_SCRIPT="./pricing_engine/push_input_to_db.sh"

if [[ ! -d "$SCENARIOS_DIR" ]]; then
  echo "Scenarios directory not found: $SCENARIOS_DIR" >&2
  exit 1
fi

if [[ ! -x "$ENGINE_BIN" ]]; then
  echo "pricing_engine binary not found, building it first..."
  make -C pricing_engine
fi

if [[ ! -x "$PUSH_SCRIPT" ]]; then
  echo "Push script is not executable: $PUSH_SCRIPT" >&2
  exit 1
fi

scenario_files=()
while IFS= read -r scenario; do
  scenario_files+=("$scenario")
done < <(find "$SCENARIOS_DIR" -maxdepth 1 -type f -name '*.yaml' | sort)

if [[ "${#scenario_files[@]}" -eq 0 ]]; then
  echo "No YAML scenarios found in: $SCENARIOS_DIR" >&2
  exit 1
fi

echo "Found ${#scenario_files[@]} scenario(s) in $SCENARIOS_DIR"

count=0
for scenario in "${scenario_files[@]}"; do
  count=$((count + 1))
  echo
  echo "[$count/${#scenario_files[@]}] Pushing $(basename "$scenario")"
  "$ENGINE_BIN" --scenario "$scenario" | "$PUSH_SCRIPT"
done

echo
echo "Done. Pushed ${#scenario_files[@]} scenario(s) into ClickHouse."
