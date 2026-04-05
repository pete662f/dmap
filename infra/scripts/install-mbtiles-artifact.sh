#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

source "${INFRA_DIR}/versions.env"

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /path/to/${DENMARK_MBTILES}" >&2
  exit 1
fi

SOURCE_MBTILES="$1"
TARGET_MBTILES="${INFRA_DIR}/data/tiles/${DENMARK_MBTILES}"

if [[ ! -f "${SOURCE_MBTILES}" ]]; then
  echo "MBTiles artifact not found: ${SOURCE_MBTILES}" >&2
  exit 1
fi

mkdir -p "$(dirname "${TARGET_MBTILES}")"
cp "${SOURCE_MBTILES}" "${TARGET_MBTILES}"

"${SCRIPT_DIR}/prepare-style-assets.sh"

echo "Installed MBTiles artifact to ${TARGET_MBTILES}"
echo "Run: docker compose -f ${INFRA_DIR}/compose.yaml up"
