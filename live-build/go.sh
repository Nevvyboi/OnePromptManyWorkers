#!/usr/bin/env bash
# Start the talk: a public tunnel first, then the crew pointed at it.
#
# Without the tunnel the QR code encodes this laptop's LAN address, so every
# person in the room has to find and join a hotspot before they can send an
# idea. With it they scan and they are in, on whatever network they are already
# using. The stage, the control page and the gallery stay on localhost, which is
# why a proxy that buffers server-sent events cannot hurt the demo: only the
# audience traffic goes through the tunnel, and the audience page polls.
#
#   ./go.sh              public tunnel, ideal
#   ./go.sh --local      no tunnel, QR points at the LAN, works offline
#
set -euo pipefail
cd "$(dirname "$0")"

KEY="${LIVE_KEY:-bbd2026}"
ARGS=(--live.key="$KEY" --live.background=true)

# The model. Set nothing and it uses whatever live.model defaults to.
if [[ -f .env.local ]]; then set -a; . ./.env.local; set +a; fi
if [[ -n "${GLM_API_KEY:-}" ]]; then
  ARGS+=(--live.model=api --live.backgroundModel=api
         --live.api.baseUrl=https://api.z.ai/api/paas/v4
         --live.api.model=glm-4.5-flash)
else
  echo "no GLM_API_KEY, falling back to the local model"
  ARGS+=(--live.model="${LIVE_MODEL:-gemma3:12b}")
fi

TUNNEL_PID=""
cleanup() { [[ -n "$TUNNEL_PID" ]] && kill "$TUNNEL_PID" 2>/dev/null || true; }
trap cleanup EXIT

if [[ "${1:-}" != "--local" ]]; then
  command -v cloudflared >/dev/null || { echo "cloudflared missing: brew install cloudflared"; exit 1; }
  LOG=$(mktemp)
  cloudflared tunnel --url http://localhost:8080 --no-autoupdate >"$LOG" 2>&1 &
  TUNNEL_PID=$!
  echo "opening a public tunnel..."
  for _ in $(seq 1 40); do
    URL=$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$LOG" 2>/dev/null | head -1) && [[ -n "$URL" ]] && break
    sleep 1
  done
  [[ -z "${URL:-}" ]] && { echo "tunnel did not come up, run with --local"; exit 1; }
  ARGS+=(--live.publicUrl="$URL")
  echo
  echo "  the room scans   $URL"
  echo "  no wifi needed, any phone on any network"
fi

echo "  stage            http://localhost:8080/stage"
echo "  control          http://localhost:8080/control?key=$KEY"
echo "  gallery          http://localhost:8080/gallery"
echo

exec mvn -q spring-boot:run -Dspring-boot.run.arguments="${ARGS[*]}"
