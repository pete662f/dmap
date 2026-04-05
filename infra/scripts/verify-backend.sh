#!/usr/bin/env bash
set -euo pipefail

MAP_BASE_URL="${1:-http://localhost:8080}"
SEARCH_BASE_URL="${2:-http://localhost:8081}"
ROUTING_BASE_URL="${3:-http://localhost:8082}"

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

echo "==> ${ROUTING_BASE_URL}/route"
curl \
  --fail \
  --silent \
  --show-error \
  --location \
  --header "Content-Type: application/json" \
  --data '{
    "locations":[
      {"lat":55.6761,"lon":12.5683},
      {"lat":55.6867,"lon":12.5701}
    ],
    "costing":"auto",
    "units":"kilometers",
    "directions_options":{"language":"da-DK"}
  }' \
  "${ROUTING_BASE_URL}/route" > /dev/null

echo
echo "Map backend is reachable at ${MAP_BASE_URL}"
echo "Search backend is reachable at ${SEARCH_BASE_URL}"
echo "Routing backend is reachable at ${ROUTING_BASE_URL}"
