#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

OUTPUT_DIR="${INFRA_DIR}/data/search/photon"
SOURCE_DATA_DIR="${OUTPUT_DIR}/photon_data"
SOURCE_MARKER_FILE="${OUTPUT_DIR}/.dataset-version"
VOLUME_NAME="${PHOTON_DATA_VOLUME_NAME:-dmap2_photon_data}"
SYNC_IMAGE="${PHOTON_SYNC_IMAGE:-alpine:3.20}"
OWNER_MARKER=".dmap2-volume-owner"
OWNER_VALUE="dmap2-photon"

if [[ ! -d "${SOURCE_DATA_DIR}" ]]; then
  echo "Photon source data directory not found at ${SOURCE_DATA_DIR}. Run bootstrap first." >&2
  exit 1
fi

if [[ ! -f "${SOURCE_MARKER_FILE}" ]]; then
  echo "Photon dataset marker not found at ${SOURCE_MARKER_FILE}. Re-run bootstrap-photon.sh." >&2
  exit 1
fi

docker volume create "${VOLUME_NAME}" >/dev/null

set +e
sync_result="$(docker run --rm \
  -v "${SOURCE_DATA_DIR}:/source-data:ro" \
  -v "${SOURCE_MARKER_FILE}:/source-marker:ro" \
  -v "${VOLUME_NAME}:/target" \
  "${SYNC_IMAGE}" \
  sh -eu -c '
    mkdir -p /target
    owner_marker="/target/'"${OWNER_MARKER}"'"
    owner_value="'"${OWNER_VALUE}"'"
    incoming="/target/.incoming-photon-data"

    target_has_files=0
    if find /target -mindepth 1 -maxdepth 1 -print -quit | grep -q .; then
      target_has_files=1
    fi

    if [ "${target_has_files}" -eq 1 ]; then
      if [ ! -f "${owner_marker}" ]; then
        printf "refusing-to-sync-unowned-volume\n"
        exit 42
      fi
      if [ "$(cat "${owner_marker}")" != "${owner_value}" ]; then
        printf "refusing-to-sync-wrong-owner\n"
        exit 43
      fi
    fi

    if [ "${target_has_files}" -eq 1 ] &&
       [ -f /target/.dataset-version ] &&
       [ -f "${owner_marker}" ] &&
       [ "$(cat "${owner_marker}")" = "${owner_value}" ] &&
       cmp -s /source-marker /target/.dataset-version; then
      chown -R 1001:1001 /target
      printf "up-to-date\n"
      exit 0
    fi

    rm -rf "${incoming}"
    mkdir -p "${incoming}"
    cp -R /source-data/. "${incoming}/"
    cp /source-marker "${incoming}/.dataset-version"
    printf "%s\n" "${owner_value}" > "${incoming}/'"${OWNER_MARKER}"'"
    chown -R 1001:1001 "${incoming}"

    find /target -mindepth 1 -maxdepth 1 ! -name .incoming-photon-data -exec rm -rf {} +
    cp -R "${incoming}/." /target/
    rm -rf "${incoming}"
    chown -R 1001:1001 /target
    printf "synced\n"
  ')"
sync_status=$?
set -e

echo "==> Photon runtime volume ${VOLUME_NAME}: ${sync_result}"
if (( sync_status != 0 )); then
  echo "Photon runtime volume sync failed." >&2
  exit "${sync_status}"
fi
