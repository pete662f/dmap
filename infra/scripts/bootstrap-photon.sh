#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

source "${INFRA_DIR}/versions.env"

CACHE_DIR="${INFRA_DIR}/.cache/photon"
ARTIFACTS_DIR="${CACHE_DIR}/artifacts"
EXTRACT_DIR="${CACHE_DIR}/extract"
OUTPUT_DIR="${INFRA_DIR}/data/search/photon"

FORCE_REBUILD="${FORCE_REBUILD:-0}"
NO_REFRESH="${NO_REFRESH:-0}"

PHOTON_JAR_FILE="photon-${PHOTON_VERSION}.jar"
PHOTON_JAR_URL="https://github.com/komoot/photon/releases/download/${PHOTON_VERSION}/${PHOTON_JAR_FILE}"
PHOTON_DB_ARCHIVE_FILE="photon-db-denmark-1.0-latest.tar.bz2"
PHOTON_DB_ARCHIVE_URL="https://download1.graphhopper.com/public/europe/denmark/${PHOTON_DB_ARCHIVE_FILE}"
PHOTON_DB_CHECKSUM_URL="${PHOTON_DB_ARCHIVE_URL}.md5"

mkdir -p "${ARTIFACTS_DIR}" "${EXTRACT_DIR}" "${OUTPUT_DIR}"

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
  rm -rf "${OUTPUT_DIR}/photon_data" "${OUTPUT_DIR}/photon.jar" "${EXTRACT_DIR:?}/"*
fi

jar_target="${OUTPUT_DIR}/photon.jar"
db_target="${OUTPUT_DIR}/photon_data"

db_archive_file="${ARTIFACTS_DIR}/${PHOTON_DB_ARCHIVE_FILE}"
db_checksum_file="${ARTIFACTS_DIR}/${PHOTON_DB_ARCHIVE_FILE}.md5"

echo "==> Preparing Photon ${PHOTON_VERSION}"
download_if_needed "${PHOTON_JAR_URL}" "${jar_target}"

if [[ ! -d "${db_target}" || "${NO_REFRESH}" != "1" ]]; then
  download_if_needed "${PHOTON_DB_ARCHIVE_URL}" "${db_archive_file}"
  download_if_needed "${PHOTON_DB_CHECKSUM_URL}" "${db_checksum_file}"

  if ! verify_md5 "${db_archive_file}" "$(checksum_value "${db_checksum_file}")"; then
    echo "Photon Denmark dump checksum verification failed." >&2
    exit 1
  fi

  rm -rf "${EXTRACT_DIR:?}/"*
  tar -xjf "${db_archive_file}" -C "${EXTRACT_DIR}"

  extracted_photon_data="$(find "${EXTRACT_DIR}" -type d -name photon_data | head -n 1 || true)"
  if [[ -z "${extracted_photon_data}" ]]; then
    echo "Could not find photon_data after extracting ${PHOTON_DB_ARCHIVE_FILE}." >&2
    exit 1
  fi

  rm -rf "${db_target}"
  mv "${extracted_photon_data}" "${db_target}"
fi

echo "==> Photon artifacts ready"
echo "Jar: ${jar_target}"
echo "Data: ${db_target}"
