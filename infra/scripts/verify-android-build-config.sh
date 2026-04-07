#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${INFRA_DIR}/.." && pwd)"
ANDROID_DIR="${REPO_DIR}/android"

BUILD_TYPE="${1:-debug}"

usage() {
  cat <<'EOF'
Usage: ./infra/scripts/verify-android-build-config.sh [debug|release]

Inspect the generated Android BuildConfig and print the compiled backend URLs.
Run this after building the APK to confirm the app was compiled for the
expected backend host.
EOF
}

case "${BUILD_TYPE}" in
  debug|release)
    ;;
  -h|--help)
    usage
    exit 0
    ;;
  *)
    echo "Unsupported build type: ${BUILD_TYPE}" >&2
    usage >&2
    exit 1
    ;;
esac

BUILD_CONFIG_FILE="${ANDROID_DIR}/app/build/generated/source/buildConfig/${BUILD_TYPE}/com/dmap/BuildConfig.java"

if [[ ! -f "${BUILD_CONFIG_FILE}" ]]; then
  BUILD_HINT="./infra/scripts/build-apk.sh"
  if [[ "${BUILD_TYPE}" == "release" ]]; then
    BUILD_HINT="./infra/scripts/build-apk.sh --release"
  fi

  echo "BuildConfig not found at ${BUILD_CONFIG_FILE}" >&2
  echo "Build the app first with ${BUILD_HINT}" >&2
  exit 1
fi

extract_field() {
  local field_name="$1"
  sed -n "s/.*${field_name} = \"\\(.*\\)\";.*/\\1/p" "${BUILD_CONFIG_FILE}" | tail -n 1
}

MAP_BACKEND_URL="$(extract_field "MAP_BACKEND_URL")"
SEARCH_BACKEND_URL="$(extract_field "SEARCH_BACKEND_URL")"
ROUTING_BACKEND_URL="$(extract_field "ROUTING_BACKEND_URL")"

echo "Compiled Android backend configuration (${BUILD_TYPE}):"
echo "  MAP_BACKEND_URL=${MAP_BACKEND_URL}"
echo "  SEARCH_BACKEND_URL=${SEARCH_BACKEND_URL}"
echo "  ROUTING_BACKEND_URL=${ROUTING_BACKEND_URL}"

if [[ "${MAP_BACKEND_URL}" == "http://10.0.2.2:8080" ]]; then
  echo
  echo "Warning: the app is still compiled with emulator defaults." >&2
  echo "Rebuild after setting android/local.properties or the repo root .env." >&2
fi
