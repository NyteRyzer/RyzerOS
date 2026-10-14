#!/bin/sh
# Removes Ryzer OS. Keeps your custom themes/settings unless --all is given.
set -u
if [ -d /storage/.config ]; then D=/storage/ryzer; P=/storage/roms/ports
elif [ -d /userdata/system ]; then D=/userdata/ryzer; P=/userdata/roms/ports
elif [ -d /mnt/mmc/MUOS ]; then D=/mnt/mmc/ryzer; P=/mnt/mmc/ROMS/Ports
elif [ -d /roms/ports ]; then D=/roms/ryzer; P=/roms/ports
else D="$HOME/ryzer"; P="$HOME/ryzer-ports"
fi
DEST="${RYZER_DEST:-$D}"
PORTS="${RYZER_PORTS:-$P}"

[ -x "$DEST/system/ryzer-perf" ] && "$DEST/system/ryzer-perf" balanced
if command -v systemctl >/dev/null 2>&1; then
	systemctl disable ryzer-tune.service 2>/dev/null || true
fi
rm -f /storage/.config/system.d/ryzer-tune.service /etc/systemd/system/ryzer-tune.service "$PORTS/Ryzer OS.sh"
rm -rf "$DEST/app" "$DEST/system" "$DEST/pylib"
if [ "${1:-}" = "--all" ]; then
	rm -rf "$DEST"
	echo "[ryzer] Removed Ryzer OS and all its data."
else
	echo "[ryzer] Removed Ryzer OS. Themes and settings kept in $DEST (use --all to delete)."
fi
