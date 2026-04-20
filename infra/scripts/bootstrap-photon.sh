#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

source "${SCRIPT_DIR}/load-env.sh"
dmap_load_env
source "${INFRA_DIR}/versions.env"

CACHE_DIR="${INFRA_DIR}/.cache/photon"
ARTIFACTS_DIR="${CACHE_DIR}/artifacts"
OUTPUT_DIR="${INFRA_DIR}/data/search/photon"
DATASET_MARKER_FILE="${OUTPUT_DIR}/.dataset-version"

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
  local tmp_output="${output}.tmp"

  if [[ -f "${output}" && "${NO_REFRESH}" == "1" ]]; then
    return
  fi

  rm -f "${tmp_output}"
  curl --fail --silent --show-error --location "${url}" --output "${tmp_output}"
  mv "${tmp_output}" "${output}"
}

remove_if_symlink() {
  local path="$1"
  local description="$2"

  if [[ -L "${path}" ]]; then
    echo "==> Removing symlinked ${description} at ${path}"
    unlink "${path}"
  fi
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

verify_sha256_if_configured() {
  local file="$1"
  local expected="$2"
  local actual

  if [[ -z "${expected}" ]]; then
    return 0
  fi

  if command -v shasum >/dev/null 2>&1; then
    actual="$(shasum -a 256 "${file}" | awk '{print $1}')"
  else
    actual="$(sha256sum "${file}" | awk '{print $1}')"
  fi

  if [[ "${actual}" != "${expected}" ]]; then
    echo "SHA256 verification failed for ${file}." >&2
    echo "Expected: ${expected}" >&2
    echo "Actual:   ${actual}" >&2
    exit 1
  fi
}

if [[ "${FORCE_REBUILD}" == "1" ]]; then
  rm -rf "${OUTPUT_DIR}/photon_data" "${OUTPUT_DIR}/photon.jar" "${DATASET_MARKER_FILE}"
fi

jar_target="${OUTPUT_DIR}/photon.jar"
db_target="${OUTPUT_DIR}/photon_data"

dump_file="${ARTIFACTS_DIR}/${PHOTON_DUMP_FILE}"
dump_checksum_file="${ARTIFACTS_DIR}/${PHOTON_DUMP_FILE}.md5"

remove_if_symlink "${jar_target}" "Photon jar"
remove_if_symlink "${db_target}" "Photon data directory"
remove_if_symlink "${DATASET_MARKER_FILE}" "Photon dataset marker"

echo "==> Preparing Photon ${PHOTON_VERSION}"
download_if_needed "${PHOTON_JAR_URL}" "${jar_target}"
verify_sha256_if_configured "${jar_target}" "${PHOTON_JAR_SHA256:-}"
download_if_needed "${PHOTON_DUMP_CHECKSUM_URL}" "${dump_checksum_file}"

if [[ ! -d "${db_target}" || "${NO_REFRESH}" != "1" ]]; then
  if ! command -v zstd >/dev/null 2>&1; then
    echo "zstd is required to import the Photon json dump." >&2
    exit 1
  fi

  download_if_needed "${PHOTON_DUMP_URL}" "${dump_file}"
  verify_sha256_if_configured "${dump_file}" "${PHOTON_DUMP_SHA256:-}"

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

cat > "${DATASET_MARKER_FILE}" <<EOF
photon_version=${PHOTON_VERSION}
dump_series=${PHOTON_DUMP_SERIES}
dump_md5=$(checksum_value "${dump_checksum_file}")
EOF

echo "==> Photon artifacts ready"
echo "Jar: ${jar_target}"
echo "Data: ${db_target}"
echo "Dataset marker: ${DATASET_MARKER_FILE}"
