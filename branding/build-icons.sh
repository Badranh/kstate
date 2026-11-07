#!/bin/sh
# Builds every app icon from branding/logo.svg. Needs rsvg-convert (brew install librsvg) and macOS iconutil.
set -eu
cd "$(dirname "$0")/.."
work=$(mktemp -d)
set=$work/kstate.iconset
mkdir "$set"
for size in 16 32 128 256 512; do
  rsvg-convert -w $size -h $size branding/logo.svg -o "$set/icon_${size}x${size}.png"
  rsvg-convert -w $((size * 2)) -h $((size * 2)) branding/logo.svg -o "$set/icon_${size}x${size}@2x.png"
done
mkdir -p host/host-desktop/icons
iconutil -c icns "$set" -o host/host-desktop/icons/kstate.icns
rsvg-convert -w 512 -h 512 branding/logo.svg -o host/host-desktop/src/main/resources/kstate-icon.png
cp branding/logo.svg viewer/viewer-web/src/wasmJsMain/resources/favicon.svg
rm -r "$work"
echo "icons written"
