#!/usr/bin/env bash
# Rebuild the deck with a QR code that actually works.
#
# The address the room scans changes every time the tunnel restarts, so the QR
# can never be typed into the generator by hand and trusted. This asks the
# running app what its address is right now and builds the deck against that.
#
#   ./go.sh            (in live-build, leave it running)
#   ./deck.sh          (here, in another terminal)
#
# Run it again any time you restart the app. It takes a few seconds.
set -euo pipefail
cd "$(dirname "$0")"

APP="${APP:-http://localhost:8080}"

URL=$(curl -s -m 5 "$APP/api/info" 2>/dev/null \
      | python3 -c 'import json,sys; print(json.load(sys.stdin).get("audienceUrl",""))' 2>/dev/null || true)

if [[ -z "$URL" ]]; then
  echo "  the app is not answering on $APP"
  echo "  start it first:  cd ../live-build && ./go.sh"
  exit 1
fi

case "$URL" in
  *localhost*|*127.0.0.1*)
    echo "  the app is advertising $URL"
    echo "  that only works on this laptop. Start it with ./go.sh so it opens a tunnel."
    exit 1 ;;
  http://192.168.*|http://10.*|http://172.*)
    echo "  warning: $URL is a LAN address, so the room must join your wifi."
    echo "  carry on only if that is what you want."
    read -r -p "  build anyway? [y/N] " yn
    [[ "$yn" == "y" || "$yn" == "Y" ]] || exit 1 ;;
esac

echo "  baking this into the QR slides:"
echo "    $URL"
JOIN_URL="$URL" node generator/build-light.js
echo "  done. Reopen the deck in PowerPoint to pick up the new QR."
