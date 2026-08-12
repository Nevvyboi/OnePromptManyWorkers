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
cleanup() { [[ -n "$TUNNEL_PID" ]] && kill "$TUNNEL_PID" 2>/dev/null; pkill -f "ssh -N .*172.18.0.1:8080" 2>/dev/null; true; }
trap cleanup EXIT

if [[ "${1:-}" != "--local" ]]; then
  # A name we own, pointed at this laptop through nginx on the floati box.
  # ssh puts port 8080 on that server's docker bridge; nginx holds the
  # certificate and forwards to it. The address never changes, so the QR on
  # the slide stays correct even if this is restarted mid talk.
  HOST="${TUNNEL_HOST:-floati}"
  URL="${PUBLIC_URL:-https://live.floati.life}"
  command -v ssh >/dev/null || { echo "ssh missing"; exit 1; }
  echo "opening the tunnel to $HOST..."
  # Keep it up. A dropped tunnel shows the room "the crew is not awake yet",
  # so if ssh dies we dial straight back rather than waiting to be noticed.
  (
    while true; do
      ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -o ServerAliveCountMax=3 \
          -o StrictHostKeyChecking=accept-new \
          -R 172.18.0.1:8080:127.0.0.1:8080 "$HOST" 2>/dev/null
      echo "  tunnel dropped, redialling..." >&2
      sleep 2
    done
  ) &
  TUNNEL_PID=$!
  # wait for the far end to actually be listening, not just for ssh to start
  for _ in $(seq 1 20); do
    ssh -o BatchMode=yes -o ConnectTimeout=5 "$HOST" \
        'ss -ltn 2>/dev/null | grep -q "172.18.0.1:8080"' 2>/dev/null && break
    sleep 1
  done
  ssh -o BatchMode=yes "$HOST" 'ss -ltn 2>/dev/null | grep -q "172.18.0.1:8080"' 2>/dev/null \
    && echo "  tunnel confirmed on the far end" \
    || { echo "  tunnel did not come up. Run with --local, or check: ssh $HOST"; exit 1; }
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
