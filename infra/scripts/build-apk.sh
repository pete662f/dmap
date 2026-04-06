#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${INFRA_DIR}/.." && pwd)"
ANDROID_DIR="${REPO_DIR}/android"
LOCAL_PROPERTIES_FILE="${ANDROID_DIR}/local.properties"

source "${SCRIPT_DIR}/load-env.sh"
dmap_load_env

BUILD_TYPE="debug"
CLEAN_BUILD=0
MAP_BACKEND_URL="${DMAP_BACKEND_URL:-}"
SEARCH_BACKEND_URL="${DMAP_SEARCH_BACKEND_URL:-}"
ROUTING_BACKEND_URL="${DMAP_ROUTING_BACKEND_URL:-}"

usage() {
  cat <<'EOF'
Usage: ./infra/scripts/build-apk.sh [options]

Build the Android APK and print the output path.

Options:
  --debug                     Build the debug APK (default)
  --release                   Build the release APK
  --clean                     Run a clean build first
  --backend-url URL           Override dmap.backendUrl
  --search-backend-url URL    Override dmap.searchBackendUrl
  --routing-backend-url URL   Override dmap.routingBackendUrl
  -h, --help                  Show this help

Environment variable fallbacks:
  DMAP_BACKEND_URL
  DMAP_SEARCH_BACKEND_URL
  DMAP_ROUTING_BACKEND_URL
  ANDROID_HOME / ANDROID_SDK_ROOT

Build config precedence:
  command-line overrides > android/local.properties > repo .env > defaults
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --debug)
      BUILD_TYPE="debug"
      shift
      ;;
    --release)
      BUILD_TYPE="release"
      shift
      ;;
    --clean)
      CLEAN_BUILD=1
      shift
      ;;
    --backend-url)
      MAP_BACKEND_URL="${2:-}"
      shift 2
      ;;
    --search-backend-url)
      SEARCH_BACKEND_URL="${2:-}"
      shift 2
      ;;
    --routing-backend-url)
      ROUTING_BACKEND_URL="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

infer_sdk_dir() {
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    printf '%s\n' "${ANDROID_HOME}"
    return
  fi

  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    printf '%s\n' "${ANDROID_SDK_ROOT}"
    return
  fi

  if [[ -f "${LOCAL_PROPERTIES_FILE}" ]]; then
    local sdk_dir
    sdk_dir="$(sed -n 's/^sdk\.dir=//p' "${LOCAL_PROPERTIES_FILE}" | tail -n 1)"
    if [[ -n "${sdk_dir}" ]]; then
      sdk_dir="${sdk_dir//\\:/:}"
      sdk_dir="${sdk_dir//\\\\/\\}"
      printf '%s\n' "${sdk_dir}"
      return
    fi
  fi

  if [[ -d "${HOME}/Library/Android/sdk" ]]; then
    printf '%s\n' "${HOME}/Library/Android/sdk"
    return
  fi

  return 1
}

infer_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    printf '%s\n' "${JAVA_HOME}"
    return
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local java_home

    java_home="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [[ -n "${java_home}" && -x "${java_home}/bin/java" ]]; then
      printf '%s\n' "${java_home}"
      return
    fi

    java_home="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    if [[ -n "${java_home}" && -x "${java_home}/bin/java" ]]; then
      printf '%s\n' "${java_home}"
      return
    fi
  fi

  if command -v java >/dev/null 2>&1; then
    local java_bin
    java_bin="$(command -v java)"
    printf '%s\n' "$(cd "$(dirname "${java_bin}")/.." && pwd)"
    return
  fi

  return 1
}

SDK_DIR="$(infer_sdk_dir || true)"
if [[ -z "${SDK_DIR}" || ! -d "${SDK_DIR}" ]]; then
  echo "Android SDK not found. Set ANDROID_HOME or add sdk.dir to android/local.properties." >&2
  exit 1
fi

JAVA_DIR="$(infer_java_home || true)"
if [[ -z "${JAVA_DIR}" || ! -x "${JAVA_DIR}/bin/java" ]]; then
  echo "A supported JDK was not found. Install Java 17 or 21, or set JAVA_HOME." >&2
  exit 1
fi

export ANDROID_HOME="${SDK_DIR}"
export ANDROID_SDK_ROOT="${SDK_DIR}"
export JAVA_HOME="${JAVA_DIR}"
export PATH="${JAVA_HOME}/bin:${PATH}"

GRADLE_ARGS=()
if [[ -n "${MAP_BACKEND_URL}" ]]; then
  GRADLE_ARGS+=("-Pdmap.backendUrl=${MAP_BACKEND_URL}")
fi
if [[ -n "${SEARCH_BACKEND_URL}" ]]; then
  GRADLE_ARGS+=("-Pdmap.searchBackendUrl=${SEARCH_BACKEND_URL}")
fi
if [[ -n "${ROUTING_BACKEND_URL}" ]]; then
  GRADLE_ARGS+=("-Pdmap.routingBackendUrl=${ROUTING_BACKEND_URL}")
fi

GRADLE_TASK="assembleDebug"
APK_RELATIVE_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [[ "${BUILD_TYPE}" == "release" ]]; then
  GRADLE_TASK="assembleRelease"
  APK_RELATIVE_PATH="app/build/outputs/apk/release/app-release.apk"
fi

cd "${ANDROID_DIR}"

if [[ "${CLEAN_BUILD}" == "1" ]]; then
  ./gradlew --no-daemon clean "${GRADLE_ARGS[@]}"
fi

./gradlew --no-daemon "${GRADLE_TASK}" "${GRADLE_ARGS[@]}"

APK_PATH="${ANDROID_DIR}/${APK_RELATIVE_PATH}"
if [[ ! -f "${APK_PATH}" ]]; then
  echo "Expected APK not found at ${APK_PATH}" >&2
  exit 1
fi

echo
echo "APK built successfully:"
echo "${APK_PATH}"
