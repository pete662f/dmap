#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

source "${SCRIPT_DIR}/load-env.sh"
dmap_load_env
source "${INFRA_DIR}/versions.env"

CACHE_DIR="${INFRA_DIR}/.cache"
PLANETILER_DIR="${CACHE_DIR}/planetiler"
PLANETILER_OUTPUT_DIR="${PLANETILER_DIR}/output"
PLANETILER_SOURCES_DIR="${PLANETILER_DIR}/sources"
OUTPUT_MBTILES="${INFRA_DIR}/data/tiles/${DENMARK_MBTILES}"
OUTPUT_OSM_DIR="${INFRA_DIR}/data/osm"

FORCE_REBUILD="${FORCE_REBUILD:-0}"
NO_REFRESH="${NO_REFRESH:-0}"
PLANETILER_JAVA_XMX="${PLANETILER_JAVA_XMX:-6g}"

resolve_threads() {
  if [[ -n "${PLANETILER_THREADS:-}" ]]; then
    printf '%s\n' "${PLANETILER_THREADS}"
    return
  fi

  if command -v nproc >/dev/null 2>&1; then
    nproc
    return
  fi

  if command -v sysctl >/dev/null 2>&1; then
    sysctl -n hw.logicalcpu
    return
  fi

  printf '%s\n' "4"
}

ensure_docker() {
  if ! docker info >/dev/null 2>&1; then
    echo "Docker is required and does not appear to be running." >&2
    exit 1
  fi
}

mkdir -p "${CACHE_DIR}" "${INFRA_DIR}/data/tiles" "${OUTPUT_OSM_DIR}" "${PLANETILER_OUTPUT_DIR}" "${PLANETILER_SOURCES_DIR}"
ensure_docker

PLANETILER_THREADS="$(resolve_threads)"
PLANETILER_OUTPUT_MBTILES="${PLANETILER_OUTPUT_DIR}/${DENMARK_MBTILES}"

if [[ "${FORCE_REBUILD}" == "1" ]]; then
  rm -f "${OUTPUT_MBTILES}" "${PLANETILER_OUTPUT_MBTILES}"
fi

if [[ ! -f "${OUTPUT_MBTILES}" ]]; then
  echo "==> Building ${DENMARK_MBTILES} with Planetiler OpenMapTiles profile"
  echo "==> Docker image: ${PLANETILER_IMAGE}"
  echo "==> Threads: ${PLANETILER_THREADS}"
  echo "==> Java heap: ${PLANETILER_JAVA_XMX}"

  planetiler_args=(
    run
    --rm
    -e "JAVA_TOOL_OPTIONS=-Xmx${PLANETILER_JAVA_XMX}"
    -v "${PLANETILER_DIR}:/data"
    "${PLANETILER_IMAGE}"
    --download
    "--area=${DENMARK_AREA}"
    "--output=/data/output/${DENMARK_MBTILES}"
    "--threads=${PLANETILER_THREADS}"
    --force=true
  )

  if [[ "${NO_REFRESH}" == "1" ]]; then
    planetiler_args+=(--refresh_sources=false)
  else
    planetiler_args+=(--refresh_sources=true)
  fi

  docker "${planetiler_args[@]}"

  cp "${PLANETILER_OUTPUT_MBTILES}" "${OUTPUT_MBTILES}"

  raw_extract="$(find "${PLANETILER_SOURCES_DIR}" -maxdepth 1 -type f -name '*denmark*.osm.pbf' | head -n 1 || true)"
  if [[ -n "${raw_extract}" ]]; then
    cp "${raw_extract}" "${OUTPUT_OSM_DIR}/$(basename "${raw_extract}")"
  fi
else
  echo "==> Reusing existing ${OUTPUT_MBTILES}"
fi

echo "==> Preparing self-hosted style assets"
"${SCRIPT_DIR}/prepare-style-assets.sh"

echo "==> Preparing self-hosted search assets"
"${SCRIPT_DIR}/bootstrap-photon.sh"

echo
echo "Bootstrap complete."
echo "MBTiles: ${OUTPUT_MBTILES}"
echo "Run: ${SCRIPT_DIR}/up-backend.sh"
