#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

source "${SCRIPT_DIR}/load-env.sh"
dmap_load_env
source "${INFRA_DIR}/versions.env"

SERVICE_DIR="${INFRA_DIR}/services/poi-area-tiles"
INPUT_PBF="${POI_AREAS_INPUT_PBF:-${INFRA_DIR}/data/osm/denmark.osm.pbf}"
OUTPUT_MBTILES="${INFRA_DIR}/data/tiles/${POI_AREAS_MBTILES}"
WORK_DIR="${INFRA_DIR}/.cache/poi-areas"
GEOJSONSEQ="${WORK_DIR}/poi-areas.geojsonseq"
IMAGE_TAG="dmap2-poi-area-tiles:${TIPPECANOE_GIT_REF}"
EXTRACTOR_SCRIPT="${SERVICE_DIR}/extract_poi_area_geojson.py"
DOCKERFILE="${SERVICE_DIR}/Dockerfile"
FORCE_REBUILD="${FORCE_REBUILD:-0}"

ensure_docker() {
  if ! docker info >/dev/null 2>&1; then
    echo "Docker is required and does not appear to be running." >&2
    exit 1
  fi
}

resolve_input_pbf() {
  if [[ -f "${INPUT_PBF}" ]]; then
    printf '%s\n' "${INPUT_PBF}"
    return
  fi

  local found
  found="$(find "${INFRA_DIR}/data/osm" -maxdepth 1 -type f -name '*.osm.pbf' | sort | head -n 1 || true)"
  if [[ -n "${found}" ]]; then
    printf '%s\n' "${found}"
    return
  fi

  echo "Missing OSM PBF input. Expected ${INPUT_PBF}" >&2
  exit 1
}

needs_rebuild() {
  if [[ "${FORCE_REBUILD}" == "1" ]]; then
    return 0
  fi
  if [[ ! -f "${OUTPUT_MBTILES}" ]]; then
    return 0
  fi
  if [[ "${INPUT_PBF_PATH}" -nt "${OUTPUT_MBTILES}" ]]; then
    return 0
  fi
  for dependency in \
    "${EXTRACTOR_SCRIPT}" \
    "${DOCKERFILE}" \
    "${BASH_SOURCE[0]}" \
    "${INFRA_DIR}/versions.env"
  do
    if [[ "${dependency}" -nt "${OUTPUT_MBTILES}" ]]; then
      return 0
    fi
  done
  return 1
}

INPUT_PBF_PATH="$(resolve_input_pbf)"
INPUT_PBF_BASENAME="$(basename "${INPUT_PBF_PATH}")"

mkdir -p "${WORK_DIR}" "${INFRA_DIR}/data/tiles"
ensure_docker

if ! needs_rebuild; then
  echo "==> Reusing existing ${OUTPUT_MBTILES}"
  exit 0
fi

echo "==> Building POI area tile image ${IMAGE_TAG}"
docker build \
  --build-arg "TIPPECANOE_GIT_REF=${TIPPECANOE_GIT_REF}" \
  --build-arg "TIPPECANOE_REPO_URL=${TIPPECANOE_REPO_URL}" \
  -t "${IMAGE_TAG}" \
  "${SERVICE_DIR}"

echo "==> Extracting POI area polygons from ${INPUT_PBF_PATH}"
docker run --rm \
  --entrypoint python3 \
  -v "$(dirname "${INPUT_PBF_PATH}"):/input:ro" \
  -v "${WORK_DIR}:/work" \
  "${IMAGE_TAG}" \
  /app/extract_poi_area_geojson.py "/input/${INPUT_PBF_BASENAME}" /work/poi-areas.geojsonseq

echo "==> Building POI area MBTiles"
docker run --rm \
  --entrypoint tippecanoe \
  -v "${WORK_DIR}:/work:ro" \
  -v "${INFRA_DIR}/data/tiles:/output" \
  "${IMAGE_TAG}" \
  --force \
  --layer=poi_area \
  --minimum-zoom=12 \
  --maximum-zoom=14 \
  --extend-zooms-if-still-dropping \
  --no-feature-limit \
  --no-tile-size-limit \
  --read-parallel \
  --output="/output/${POI_AREAS_MBTILES}" \
  /work/poi-areas.geojsonseq

feature_count="$(wc -l < "${GEOJSONSEQ}" | tr -d ' ')"
echo "==> POI area features: ${feature_count}"
echo "==> POI area MBTiles: ${OUTPUT_MBTILES}"
