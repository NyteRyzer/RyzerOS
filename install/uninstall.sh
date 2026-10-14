#!/bin/sh
# Removes Ryzer OS. Keeps your custom themes/settings unless --all is given.
set -u
DEST="${RYZER_DEST:-/storage/ryzer}"
PORTS="${RYZER_PORTS:-/storage/roms/ports}"

[ -x "$DEST/system/ryzer-perf" ] && "$DEST/system/ryzer-perf" balanced
if command -v systemctl >/dev/null 2>&1; then
	systemctl disable ryzer-tune.service 2>/dev/null || true
fi
rm -f /storage/.config/system.d/ryzer-tune.service "$PORTS/Ryzer OS.sh"
rm -rf "$DEST/app" "$DEST/system" "$DEST/pylib"
if [ "${1:-}" = "--all" ]; then
	rm -rf "$DEST"
	echo "[ryzer] Removed Ryzer OS and all its data."
else
	echo "[ryzer] Removed Ryzer OS. Themes and settings kept in $DEST (use --all to delete)."
fi
