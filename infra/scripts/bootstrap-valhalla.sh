#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

ROUTING_DIR="${INFRA_DIR}/data/routing/valhalla"
OSM_INPUT="${INFRA_DIR}/data/osm/denmark.osm.pbf"
ROUTING_INPUT="${ROUTING_DIR}/denmark.osm.pbf"

FORCE_REBUILD="${FORCE_REBUILD:-0}"

if [[ ! -f "${OSM_INPUT}" ]]; then
  echo "Expected Denmark OSM extract at ${OSM_INPUT}, but it does not exist." >&2
  echo "Run ./infra/scripts/bootstrap-denmark.sh first so Planetiler stages the extract." >&2
  exit 1
fi

mkdir -p "${ROUTING_DIR}"

if [[ "${FORCE_REBUILD}" == "1" ]]; then
  find "${ROUTING_DIR}" -mindepth 1 \
    ! -name '.gitkeep' \
    ! -name 'denmark.osm.pbf' \
    -exec rm -rf {} +
fi

cp "${OSM_INPUT}" "${ROUTING_INPUT}"

echo "==> Valhalla routing input ready"
echo "PBF: ${ROUTING_INPUT}"
