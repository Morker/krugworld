#!/usr/bin/env sh
# Rebuild the app image from the jar in this folder, restart it, and wait
# until it's actually healthy (Spring takes ~30-60s to start on this box).
set -eu
cd "$(dirname "$0")"

docker compose up -d --build

printf 'Waiting for KrugWorld'
i=0
while [ $i -lt 40 ]; do
  if curl -fsS http://127.0.0.1:8095/actuator/health 2>/dev/null | grep -q '"UP"'; then
    echo ' — up.'
    exit 0
  fi
  printf '.'
  sleep 3
  i=$((i + 1))
done

echo ' — not healthy after 2 minutes. Check: docker compose logs --tail 100 app'
exit 1
