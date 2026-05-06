#!/usr/bin/env bash
set -euo pipefail

MODULE_NAME="pricing_engine"

rmmod "$MODULE_NAME" 2>/dev/null || true
insmod "${MODULE_NAME}.ko"
chown -R "$SUDO_USER":"$SUDO_USER" . 2>/dev/null || true

echo "Installed."
echo "Device: /dev/pricing_engine"
echo
head -n 10 /dev/pricing_engine
