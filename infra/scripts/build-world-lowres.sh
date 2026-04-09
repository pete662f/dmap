#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

source "${SCRIPT_DIR}/load-env.sh"
dmap_load_env
source "${INFRA_DIR}/versions.env"

CACHE_DIR="${INFRA_DIR}/.cache"
WORLD_LOWRES_DIR="${CACHE_DIR}/world-lowres"
WORLD_LOWRES_INPUT_DIR="${WORLD_LOWRES_DIR}/input"
WORLD_LOWRES_OUTPUT_DIR="${WORLD_LOWRES_DIR}/output"
WORLD_LOWRES_OUTPUT_MBTILES="${WORLD_LOWRES_OUTPUT_DIR}/${WORLD_LOWRES_MBTILES}"
TARGET_MBTILES="${INFRA_DIR}/data/tiles/${WORLD_LOWRES_MBTILES}"
TIPPECANOE_IMAGE_TAG="dmap2-tippecanoe:${TIPPECANOE_REF}"

FORCE_REBUILD="${FORCE_REBUILD:-0}"

ensure_docker() {
  if ! docker info >/dev/null 2>&1; then
    echo "Docker is required and does not appear to be running." >&2
    exit 1
  fi
}

ensure_tippecanoe_image() {
  if docker image inspect "${TIPPECANOE_IMAGE_TAG}" >/dev/null 2>&1; then
    return
  fi

  echo "==> Building local Tippecanoe image ${TIPPECANOE_IMAGE_TAG}"
  docker build \
    --build-arg "TIPPECANOE_REF=${TIPPECANOE_REF}" \
    -t "${TIPPECANOE_IMAGE_TAG}" \
    -f "${INFRA_DIR}/services/tippecanoe/Dockerfile" \
    "${INFRA_DIR}/services/tippecanoe"
}

mkdir -p "${INFRA_DIR}/data/tiles" "${WORLD_LOWRES_INPUT_DIR}" "${WORLD_LOWRES_OUTPUT_DIR}"
ensure_docker
ensure_tippecanoe_image

if [[ "${FORCE_REBUILD}" == "1" ]]; then
  rm -f "${TARGET_MBTILES}" "${WORLD_LOWRES_OUTPUT_MBTILES}"
fi

if [[ -f "${TARGET_MBTILES}" ]]; then
  echo "==> Reusing existing ${TARGET_MBTILES}"
  exit 0
fi

echo "==> Preparing low-zoom world vector source inputs"
python3 "${SCRIPT_DIR}/prepare-world-lowres.py" \
  "${NATURAL_EARTH_VECTOR_REF}" \
  "${CACHE_DIR}/world-lowres-sources" \
  "${WORLD_LOWRES_INPUT_DIR}"

echo "==> Building ${WORLD_LOWRES_MBTILES} with Tippecanoe"
docker run --rm \
  -v "${WORLD_LOWRES_DIR}:/work" \
  "${TIPPECANOE_IMAGE_TAG}" \
  --force \
  --output="/work/output/${WORLD_LOWRES_MBTILES}" \
  --minimum-zoom=0 \
  --maximum-zoom=6 \
  --name="dmap2 world lowres" \
  --description="Low-zoom world fallback tileset for dmap2" \
  -L "world_land:/work/input/world_land.geojson" \
  -L "world_water:/work/input/world_water.geojson" \
  -L "world_boundaries:/work/input/world_boundaries.geojson" \
  -L "world_countries:/work/input/world_countries.geojson" \
  -L "world_cities:/work/input/world_cities.geojson"

cp "${WORLD_LOWRES_OUTPUT_MBTILES}" "${TARGET_MBTILES}"

echo
echo "World low-res MBTiles: ${TARGET_MBTILES}"
