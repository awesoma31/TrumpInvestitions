#!/usr/bin/env bash
set -euo pipefail

SCENARIOS_DIR="${1:-pricing_engine/examples/generated}"
ENGINE_BIN="./pricing_engine/pricing_engine"
PUSH_SCRIPT="./pricing_engine/push_input_to_db.sh"
JOBS="${JOBS:-3}"

if [[ ! -d "$SCENARIOS_DIR" ]]; then
  echo "Scenarios directory not found: $SCENARIOS_DIR" >&2
  exit 1
fi

if ! [[ "$JOBS" =~ ^[1-9][0-9]*$ ]]; then
  echo "JOBS must be a positive integer, got: $JOBS" >&2
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
echo "Running up to $JOBS scenario(s) in parallel"

current_jobs=0
started=0

run_scenario() {
  local scenario="$1"
  local name
  name="$(basename "$scenario")"

  echo "[start] $name"
  "$ENGINE_BIN" --scenario "$scenario" | "$PUSH_SCRIPT"
  echo "[done ] $name"
}

for scenario in "${scenario_files[@]}"; do
  started=$((started + 1))
  run_scenario "$scenario" &
  current_jobs=$((current_jobs + 1))

  if (( current_jobs >= JOBS )); then
    wait
    current_jobs=0
  fi
done

if (( current_jobs > 0 )); then
  wait
fi

echo
echo "Done. Pushed ${#scenario_files[@]} scenario(s) into ClickHouse with parallelism $JOBS."
