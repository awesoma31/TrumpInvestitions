#!/usr/bin/env bash
set -euo pipefail

MODULE_NAME="pricing_engine"

make clean
make

#if lsmod | grep -q "^${MODULE_NAME}"; then
#  sudo rmmod "$MODULE_NAME"
#fi
rmmod "$MODULE_NAME" 2>/dev/null || true

HISTORY_HOURS="${HISTORY_HOURS:-24}"
HISTORY_QPH="${HISTORY_QPH:-20}"

sudo insmod "${MODULE_NAME}.ko" \
  history_hours="${HISTORY_HOURS}" \
  history_qph="${HISTORY_QPH}"

#insmod "${MODULE_NAME}.ko"
chown -R "$SUDO_USER":"$SUDO_USER" . 2>/dev/null || true

echo "Installed."
echo "Device:  /dev/pricing_engine"
echo "Backfill: ${HISTORY_HOURS}h × ${HISTORY_QPH} qph = $((HISTORY_HOURS * HISTORY_QPH)) historical quotes"
echo
head -n 10 /dev/pricing_engine
