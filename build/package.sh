#!/bin/sh
# Builds dist/RyzerOS-<version>.zip: copy it to the RG DS SD card, unzip, run install.sh.
#
# Bundles pygame-ce wheels for every Python version the RK3568 firmwares ship,
# so the device needs no internet connection.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(python3 -c "import sys; sys.path.insert(0, '$ROOT'); import ryzer; print(ryzer.VERSION)")"
PYGAME_VERSION="${PYGAME_VERSION:-2.5.8}"
STAGE="$ROOT/dist/RyzerOS"
OUT="$ROOT/dist/RyzerOS-$VERSION.zip"

rm -rf "$STAGE" "$OUT"
mkdir -p "$STAGE/wheels"
cp -r "$ROOT/ryzer" "$ROOT/system" "$STAGE/"
cp "$ROOT/install/install.sh" "$ROOT/install/uninstall.sh" "$ROOT/README.md" "$STAGE/"
find "$STAGE" -name "__pycache__" -type d -exec rm -rf {} +

for py in 3.10 3.11 3.12 3.13; do
	python3 -m pip download "pygame-ce==$PYGAME_VERSION" --no-deps --only-binary=:all: \
		--platform manylinux2014_aarch64 --python-version "$py" -d "$STAGE/wheels" -q \
		|| echo "warning: no pygame-ce wheel for Python $py"
done

(cd "$ROOT/dist" && python3 -m zipfile -c "RyzerOS-$VERSION.zip" RyzerOS)
echo "Built $OUT"
