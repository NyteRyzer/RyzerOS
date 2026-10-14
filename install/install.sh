#!/bin/sh
# Ryzer OS installer for Linux handheld firmwares:
#   ROCKNIX, Knulli / Batocera (tested layouts), muOS and ArkOS (experimental).
# Works on Anbernic, Retroid, AYN, Powkiddy, TrimUI... whatever the firmware supports.
#
# Run on the device (SSH or a terminal port):
#     sh install.sh            install / update
#     sh install.sh --boot     also run the speed tweaks at every boot (systemd firmwares)
#
# Adds "Ryzer OS" to the Ports menu. Your ROMs, saves and the firmware are not touched.

set -eu
SRC="$(cd "$(dirname "$0")" && pwd)"

if [ -d /storage/.config ]; then FW=rocknix; D=/storage/ryzer; P=/storage/roms/ports
elif [ -d /userdata/system ]; then FW=knulli; D=/userdata/ryzer; P=/userdata/roms/ports
elif [ -d /mnt/mmc/MUOS ]; then FW=muos; D=/mnt/mmc/ryzer; P=/mnt/mmc/ROMS/Ports
elif [ -d /roms/ports ]; then FW=arkos; D=/roms/ryzer; P=/roms/ports
else FW=unknown; D="$HOME/ryzer"; P="$HOME/ryzer-ports"
fi
DEST="${RYZER_DEST:-$D}"
PORTS="${RYZER_PORTS:-$P}"
BOOT=0
[ "${1:-}" = "--boot" ] && BOOT=1

say() { echo "[ryzer] $*"; }

command -v python3 >/dev/null 2>&1 || { say "python3 not found - this firmware can't run Ryzer OS."; exit 1; }

PYTAG="cp$(python3 -c 'import sys; print("%d%d" % sys.version_info[:2])')"
say "Firmware: $FW   Python: $PYTAG"
say "Installing to $DEST, launcher in $PORTS"

mkdir -p "$DEST" "$PORTS"
rm -rf "$DEST/app" "$DEST/system"
mkdir -p "$DEST/app"
cp -r "$SRC/ryzer" "$DEST/app/ryzer"
cp -r "$SRC/system" "$DEST/system"
chmod +x "$DEST/system/"*

# pygame-ce: use the firmware's copy if it has one, otherwise the bundled wheel.
if python3 -c 'import pygame' >/dev/null 2>&1; then
	say "Using the firmware's pygame"
else
	WHEEL="$(ls "$SRC"/wheels/pygame_ce-*-"$PYTAG"-"$PYTAG"-*aarch64*.whl 2>/dev/null | head -n1 || true)"
	if [ -z "$WHEEL" ]; then
		say "No bundled pygame-ce wheel for $PYTAG. Available:"
		ls "$SRC/wheels" 2>/dev/null || true
		exit 1
	fi
	rm -rf "$DEST/pylib"
	mkdir -p "$DEST/pylib"
	python3 -m zipfile -e "$WHEEL" "$DEST/pylib"
	say "Installed $(basename "$WHEEL")"
fi

cat > "$PORTS/Ryzer OS.sh" <<LAUNCH
#!/bin/sh
# Launches Ryzer OS. Hold SELECT + START to return to the stock frontend.
[ -f /etc/profile ] && . /etc/profile
export PYTHONPATH="$DEST/pylib:$DEST/app\${PYTHONPATH:+:\$PYTHONPATH}"
export RYZER_HOME="$DEST"
sh "$DEST/system/ryzer-tune" >/dev/null 2>&1 || true
cd "$DEST/app"
python3 -m ryzer "\$@" > "$DEST/ryzer.log" 2>&1
sh "$DEST/system/ryzer-perf" balanced >/dev/null 2>&1 || true
LAUNCH
chmod +x "$PORTS/Ryzer OS.sh"
say "Added 'Ryzer OS' to the Ports menu"

if [ "$BOOT" = 1 ]; then
	UNIT_DIR=/storage/.config/system.d
	[ "$FW" = rocknix ] || UNIT_DIR=/etc/systemd/system
	mkdir -p "$UNIT_DIR"
	cat > "$UNIT_DIR/ryzer-tune.service" <<UNIT
[Unit]
Description=Ryzer OS speed tweaks
After=multi-user.target

[Service]
Type=oneshot
ExecStart=$DEST/system/ryzer-tune

[Install]
WantedBy=multi-user.target
UNIT
	if command -v systemctl >/dev/null 2>&1; then
		systemctl daemon-reload || true
		systemctl enable ryzer-tune.service || true
		say "Speed tweaks will run at every boot"
	else
		say "systemd not found; boot tweaks not enabled (they still run when Ryzer starts)"
	fi
fi

say "Done! Open Ports > Ryzer OS. (Refresh the game list if it doesn't show yet.)"
