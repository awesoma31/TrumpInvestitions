#!/usr/bin/env bash
set -euo pipefail

MODULE_NAME="pricing_engine"

if lsmod | grep -q "^${MODULE_NAME}"; then
  sudo rmmod "$MODULE_NAME"
  echo "Module removed."
else
  echo "Module is not loaded."
fi

make clean

echo "Uninstalled."
