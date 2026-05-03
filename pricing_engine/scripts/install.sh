#!/usr/bin/env bash
set -euo pipefail

MODULE_NAME="pricing_engine"

make clean
make

if lsmod | grep -q "^${MODULE_NAME}"; then
  sudo rmmod "$MODULE_NAME"
fi

sudo insmod "${MODULE_NAME}.ko"

echo "Installed."
echo "Device: /dev/pricing_engine"
echo
head -n 10 /dev/pricing_engine
