#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "${SCRIPT_DIR}/load-env.sh"
dmap_load_env

MAP_BASE_URL="${1:-$(dmap_resolve_url DMAP_BACKEND_URL 8080 localhost)}"
SEARCH_BASE_URL="${2:-$(dmap_resolve_url DMAP_SEARCH_BACKEND_URL 8081 localhost)}"
IMAGERY_BASE_URL="${3:-$(dmap_resolve_url DMAP_IMAGERY_BACKEND_URL 8083 localhost)}"

check() {
  local url="$1"
  echo "==> ${url}"
  curl --fail --silent --show-error --location "${url}" > /dev/null
}

check "${MAP_BASE_URL}/styles/osm-liberty/style.json"
check "${MAP_BASE_URL}/styles/osm-liberty/sprite.json"
check "${MAP_BASE_URL}/styles/osm-liberty/sprite.png"
check "${MAP_BASE_URL}/fonts/Open%20Sans%20Regular/0-255.pbf"
check "${MAP_BASE_URL}/data/openmaptiles.json"
check "${MAP_BASE_URL}/data/openmaptiles/0/0/0.pbf"
check "${SEARCH_BASE_URL}/status"
check "${SEARCH_BASE_URL}/api?q=aarhus&limit=3"
check "${SEARCH_BASE_URL}/reverse?lon=12.5683&lat=55.6761&limit=1"
check "${IMAGERY_BASE_URL}/healthz"

if [[ -n "${DMAP_ORTHOFOTO_TOKEN:-}" ]]; then
  check "${IMAGERY_BASE_URL}/ortofoto/tiles/10/547/322.jpg"
else
  echo
  echo "Warning: DMAP_ORTHOFOTO_TOKEN is not set; skipping Ortofoto tile validation." >&2
fi

echo
echo "Map backend is reachable at ${MAP_BASE_URL}"
echo "Search backend is reachable at ${SEARCH_BASE_URL}"
echo "Imagery backend is reachable at ${IMAGERY_BASE_URL}"
