#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

OUTPUT_DIR="${INFRA_DIR}/data/search/photon"
SOURCE_DATA_DIR="${OUTPUT_DIR}/photon_data"
SOURCE_MARKER_FILE="${OUTPUT_DIR}/.dataset-version"
VOLUME_NAME="${PHOTON_DATA_VOLUME_NAME:-photon_data}"
SYNC_IMAGE="${PHOTON_SYNC_IMAGE:-alpine:3.20}"

if [[ ! -d "${SOURCE_DATA_DIR}" ]]; then
  echo "Photon source data directory not found at ${SOURCE_DATA_DIR}. Run bootstrap first." >&2
  exit 1
fi

if [[ ! -f "${SOURCE_MARKER_FILE}" ]]; then
  echo "Photon dataset marker not found at ${SOURCE_MARKER_FILE}. Re-run bootstrap-photon.sh." >&2
  exit 1
fi

docker volume create "${VOLUME_NAME}" >/dev/null

sync_result="$(docker run --rm \
  -v "${SOURCE_DATA_DIR}:/source-data:ro" \
  -v "${SOURCE_MARKER_FILE}:/source-marker:ro" \
  -v "${VOLUME_NAME}:/target" \
  "${SYNC_IMAGE}" \
  sh -eu -c '
    mkdir -p /target

    target_has_files=0
    if find /target -mindepth 1 -maxdepth 1 -print -quit | grep -q .; then
      target_has_files=1
    fi

    if [ "${target_has_files}" -eq 1 ] &&
       [ -f /target/.dataset-version ] &&
       cmp -s /source-marker /target/.dataset-version; then
      printf "up-to-date\n"
      exit 0
    fi

    find /target -mindepth 1 -maxdepth 1 -exec rm -rf {} +
    cp -R /source-data/. /target/
    cp /source-marker /target/.dataset-version
    printf "synced\n"
  ')"

echo "==> Photon runtime volume ${VOLUME_NAME}: ${sync_result}"
