#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

docker compose up -d --wait --wait-timeout 180 postgres
docker compose up -d --build api

api_address="$(docker compose port api 8080)"
echo "Waiting for the backend to respond..."
for ((attempt = 1; attempt <= 90; attempt++)); do
    if curl --silent --show-error --fail --max-time 2 \
        "http://${api_address}/api/v1/instruments?q=GGAL" >/dev/null 2>&1; then
        echo "Backend is ready at http://${api_address}"
        exit 0
    fi
    if [[ -z "$(docker compose ps --status running -q api)" ]]; then
        echo "Backend container stopped before becoming ready." >&2
        docker compose logs --tail=50 api >&2
        exit 1
    fi
    sleep 2
done

echo "Backend did not become ready in time." >&2
docker compose logs --tail=50 api >&2
exit 1
