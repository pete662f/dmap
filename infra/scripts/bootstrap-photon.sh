#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

source "${INFRA_DIR}/versions.env"

CACHE_DIR="${INFRA_DIR}/.cache/photon"
ARTIFACTS_DIR="${CACHE_DIR}/artifacts"
OUTPUT_DIR="${INFRA_DIR}/data/search/photon"

FORCE_REBUILD="${FORCE_REBUILD:-0}"
NO_REFRESH="${NO_REFRESH:-0}"

PHOTON_JAR_FILE="photon-${PHOTON_VERSION}.jar"
PHOTON_JAR_URL="https://github.com/komoot/photon/releases/download/${PHOTON_VERSION}/${PHOTON_JAR_FILE}"
PHOTON_DUMP_FILE="photon-dump-${DENMARK_AREA}-${PHOTON_DUMP_SERIES}-latest.jsonl.zst"
PHOTON_DUMP_URL="https://download1.graphhopper.com/public/europe/${DENMARK_AREA}/${PHOTON_DUMP_FILE}"
PHOTON_DUMP_CHECKSUM_URL="${PHOTON_DUMP_URL}.md5"
PHOTON_IMPORT_IMAGE="eclipse-temurin:21-jre-jammy"

mkdir -p "${ARTIFACTS_DIR}" "${OUTPUT_DIR}"

download_if_needed() {
  local url="$1"
  local output="$2"

  if [[ -f "${output}" && "${NO_REFRESH}" == "1" ]]; then
    return
  fi

  curl --fail --silent --show-error --location "${url}" --output "${output}"
}

checksum_value() {
  local checksum_file="$1"
  awk '{print $1}' "${checksum_file}"
}

verify_md5() {
  local file="$1"
  local expected="$2"
  local actual

  if command -v md5 >/dev/null 2>&1; then
    actual="$(md5 -q "${file}")"
  else
    actual="$(md5sum "${file}" | awk '{print $1}')"
  fi

  [[ "${actual}" == "${expected}" ]]
}

if [[ "${FORCE_REBUILD}" == "1" ]]; then
  rm -rf "${OUTPUT_DIR}/photon_data" "${OUTPUT_DIR}/photon.jar"
fi

jar_target="${OUTPUT_DIR}/photon.jar"
db_target="${OUTPUT_DIR}/photon_data"

dump_file="${ARTIFACTS_DIR}/${PHOTON_DUMP_FILE}"
dump_checksum_file="${ARTIFACTS_DIR}/${PHOTON_DUMP_FILE}.md5"

echo "==> Preparing Photon ${PHOTON_VERSION}"
download_if_needed "${PHOTON_JAR_URL}" "${jar_target}"

if [[ ! -d "${db_target}" || "${NO_REFRESH}" != "1" ]]; then
  if ! command -v zstd >/dev/null 2>&1; then
    echo "zstd is required to import the Photon json dump." >&2
    exit 1
  fi

  download_if_needed "${PHOTON_DUMP_URL}" "${dump_file}"
  download_if_needed "${PHOTON_DUMP_CHECKSUM_URL}" "${dump_checksum_file}"

  if ! verify_md5 "${dump_file}" "$(checksum_value "${dump_checksum_file}")"; then
    echo "Photon Denmark json dump checksum verification failed." >&2
    exit 1
  fi

  rm -rf "${db_target}"
  docker pull "${PHOTON_IMPORT_IMAGE}" >/dev/null

  # Build a fresh Photon DB from the official version-matched json dump.
  zstd --stdout -d "${dump_file}" | docker run --rm -i \
    -v "${OUTPUT_DIR}:/srv/photon" \
    "${PHOTON_IMPORT_IMAGE}" \
    java -jar /srv/photon/photon.jar import -data-dir /srv/photon -import-file -

  if [[ ! -d "${db_target}" ]]; then
    echo "Photon import did not produce ${db_target}." >&2
    exit 1
  fi
fi

echo "==> Photon artifacts ready"
echo "Jar: ${jar_target}"
echo "Data: ${db_target}"
