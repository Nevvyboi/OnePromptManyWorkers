#!/usr/bin/env bash
# Put the server back exactly as it was before the talk.
#
# Everything the talk added was additive and is named, so this removes it and
# nothing else: the nginx server blocks for live.floati.life, the sshd drop-in
# that let the tunnel bind the docker bridge, and optionally the certificate.
#
# The one thing this cannot do is delete the DNS record. Remove the A record
# for live.floati.life in Google Cloud DNS yourself afterwards.
set -euo pipefail
HOST="${HOST:-floati}"

echo "reverting $HOST"
ssh "$HOST" '
set -e
f=/opt/DataBrew/nginx/nginx.conf

if [ -f "${f}.before-talk" ]; then
  cp "$f" "${f}.talk-version"          # keep a copy, in case you want it back
  cp "${f}.before-talk" "$f"
  echo "  nginx.conf restored from the pre-talk backup"
else
  # no backup: strip the marked blocks instead
  sed -i "/---- BBD talk:/,/^}$/d" "$f"
  echo "  nginx.conf: talk blocks stripped"
fi

rm -f /etc/ssh/sshd_config.d/99-talk-tunnel.conf
sshd -t && (systemctl reload ssh 2>/dev/null || systemctl reload sshd 2>/dev/null) && echo "  sshd drop-in removed"

docker exec databrew-nginx-1 nginx -t >/dev/null 2>&1 && \
  docker exec databrew-nginx-1 nginx -s reload >/dev/null 2>&1 && echo "  nginx reloaded"
'

echo "checking the sites that matter are still up"
for d in floati.life databrew.works api.redlineclause.com; do
  printf "  %-24s %s\n" "$d" "$(curl -s -m 10 -o /dev/null -w '%{http_code}' "https://$d" || echo unreachable)"
done

cat <<'NOTE'

Two things left, both yours:
  1. delete the A record for live.floati.life in Google Cloud DNS
  2. optional, drop the certificate:
       ssh floati 'certbot delete --cert-name live.floati.life'
NOTE
