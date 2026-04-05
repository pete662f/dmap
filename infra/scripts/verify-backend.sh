#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

check() {
  local url="$1"
  echo "==> ${url}"
  curl --fail --silent --show-error --location "${url}" > /dev/null
}

check "${BASE_URL}/styles/osm-liberty/style.json"
check "${BASE_URL}/styles/osm-liberty/sprite.json"
check "${BASE_URL}/styles/osm-liberty/sprite.png"
check "${BASE_URL}/fonts/Open%20Sans%20Regular/0-255.pbf"
check "${BASE_URL}/data/openmaptiles.json"
check "${BASE_URL}/data/openmaptiles/0/0/0.pbf"

echo
echo "Backend is reachable at ${BASE_URL}"
