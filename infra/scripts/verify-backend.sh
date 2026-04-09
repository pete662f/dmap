#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "${SCRIPT_DIR}/load-env.sh"
dmap_load_env

MAP_BASE_URL="${1:-$(dmap_resolve_url DMAP_BACKEND_URL 8080 localhost)}"
SEARCH_BASE_URL="${2:-$(dmap_resolve_url DMAP_SEARCH_BACKEND_URL 8081 localhost)}"

check() {
  local url="$1"
  echo "==> ${url}"
  curl --fail --silent --show-error --location "${url}" > /dev/null
}

assert_style_contains() {
  local style_url="$1"
  echo "==> validating ${style_url}"
  python3 - "$style_url" <<'PY'
import json
import sys
import urllib.request

style_url = sys.argv[1]
with urllib.request.urlopen(style_url) as response:
    style = json.loads(response.read().decode("utf-8"))

metadata = style.get("metadata", {})
if metadata.get("dmap:world_lowres") != "natural-earth-vector":
    raise SystemExit("Style endpoint is missing the world-lowres metadata marker.")

sources = style.get("sources", {})
world_source = sources.get("world-lowres")
if world_source is None:
    raise SystemExit("Style endpoint is missing the world-lowres source.")

if world_source.get("type") != "vector":
    raise SystemExit("world-lowres source is not a vector source.")

if any(
    isinstance(source, dict) and "world-reference" in str(source.get("data", ""))
    for source in sources.values()
):
    raise SystemExit("Style endpoint still references the removed world-reference GeoJSON path.")

layers = style.get("layers", [])
layer_index = {layer.get("id"): index for index, layer in enumerate(layers)}
required_layers = [
    "dmap-world-water",
    "dmap-world-land",
    "dmap-world-country-borders",
    "dmap-world-country-labels",
    "dmap-world-major-cities",
]
for layer_id in required_layers:
    if layer_id not in layer_index:
        raise SystemExit(f"Style endpoint is missing layer {layer_id}.")

if layer_index["dmap-world-country-labels"] <= layer_index["water"]:
    raise SystemExit("World country labels render below water.")

if layer_index["dmap-world-major-cities"] <= layer_index["water"]:
    raise SystemExit("World major city labels render below water.")
PY
}

check "${MAP_BASE_URL}/styles/osm-liberty/style.json"
assert_style_contains "${MAP_BASE_URL}/styles/osm-liberty/style.json"
check "${MAP_BASE_URL}/styles/osm-liberty/sprite.json"
check "${MAP_BASE_URL}/styles/osm-liberty/sprite.png"
check "${MAP_BASE_URL}/fonts/Open%20Sans%20Regular/0-255.pbf"
check "${MAP_BASE_URL}/data/openmaptiles.json"
check "${MAP_BASE_URL}/data/openmaptiles/0/0/0.pbf"
check "${MAP_BASE_URL}/data/world-lowres.json"
check "${MAP_BASE_URL}/data/world-lowres/0/0/0.pbf"
check "${MAP_BASE_URL}/data/world-lowres/1/0/0.pbf"
check "${SEARCH_BASE_URL}/status"
check "${SEARCH_BASE_URL}/api?q=aarhus&limit=3"
check "${SEARCH_BASE_URL}/reverse?lon=12.5683&lat=55.6761&limit=1"

echo
echo "Map backend is reachable at ${MAP_BASE_URL}"
echo "Search backend is reachable at ${SEARCH_BASE_URL}"
