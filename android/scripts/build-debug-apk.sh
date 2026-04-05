#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ROOT_DIR="$(cd "${ANDROID_DIR}/.." && pwd)"
LOCAL_PROPERTIES="${ANDROID_DIR}/local.properties"
ENV_FILE="${ROOT_DIR}/.env"
APK_PATH="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"

resolve_sdk_dir() {
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    printf '%s\n' "${ANDROID_HOME}"
    return
  fi

  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    printf '%s\n' "${ANDROID_SDK_ROOT}"
    return
  fi

  if [[ -f "${LOCAL_PROPERTIES}" ]]; then
    local sdk_dir
    sdk_dir="$(grep '^sdk\.dir=' "${LOCAL_PROPERTIES}" | head -n 1 | cut -d'=' -f2- || true)"
    sdk_dir="${sdk_dir//\\:/:}"
    sdk_dir="${sdk_dir//\\\\/\\}"
    if [[ -n "${sdk_dir}" ]]; then
      printf '%s\n' "${sdk_dir}"
      return
    fi
  fi

  printf '%s\n' "${HOME}/Library/Android/sdk"
}

resolve_java_home() {
  if [[ -n "${JAVA_HOME:-}" ]]; then
    printf '%s\n' "${JAVA_HOME}"
    return
  fi

  local default_java_home="/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home"
  if [[ -d "${default_java_home}" ]]; then
    printf '%s\n' "${default_java_home}"
    return
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    /usr/libexec/java_home -v 17 2>/dev/null && return
  fi

  printf '%s\n' ""
}

load_env_file() {
  if [[ -f "${ENV_FILE}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
    set +a
  fi
}

normalize_url() {
  local value="$1"
  value="${value%/}"
  printf '%s\n' "${value}"
}

resolve_backend_url() {
  local explicit_url="$1"
  local port="$2"

  if [[ -n "${explicit_url}" ]]; then
    normalize_url "${explicit_url}"
    return
  fi

  if [[ -n "${DMAP_HOST_IP:-}" ]]; then
    normalize_url "http://${DMAP_HOST_IP}:${port}"
    return
  fi

  printf '%s\n' ""
}

load_env_file

SDK_DIR="$(resolve_sdk_dir)"
JAVA_HOME_VALUE="$(resolve_java_home)"
MAP_BACKEND_URL_VALUE="$(resolve_backend_url "${DMAP_BACKEND_URL:-}" "8080")"
SEARCH_BACKEND_URL_VALUE="$(resolve_backend_url "${DMAP_SEARCH_BACKEND_URL:-}" "8081")"
ROUTING_BACKEND_URL_VALUE="$(resolve_backend_url "${DMAP_ROUTING_BACKEND_URL:-}" "8082")"

if [[ ! -d "${SDK_DIR}" ]]; then
  echo "Android SDK not found at ${SDK_DIR}." >&2
  echo "Set sdk.dir in ${LOCAL_PROPERTIES} or export ANDROID_HOME." >&2
  exit 1
fi

if [[ -z "${JAVA_HOME_VALUE}" || ! -d "${JAVA_HOME_VALUE}" ]]; then
  echo "Java 17 not found." >&2
  echo "Export JAVA_HOME or install a Java 17 JDK." >&2
  exit 1
fi

echo "==> Rebuilding debug APK"
echo "Repo: ${ROOT_DIR}"
echo "JAVA_HOME: ${JAVA_HOME_VALUE}"
echo "ANDROID_HOME: ${SDK_DIR}"
if [[ -f "${ENV_FILE}" ]]; then
  echo "ENV_FILE: ${ENV_FILE}"
fi
if [[ -n "${MAP_BACKEND_URL_VALUE}" ]]; then
  echo "MAP_BACKEND_URL: ${MAP_BACKEND_URL_VALUE}"
fi
if [[ -n "${SEARCH_BACKEND_URL_VALUE}" ]]; then
  echo "SEARCH_BACKEND_URL: ${SEARCH_BACKEND_URL_VALUE}"
fi
if [[ -n "${ROUTING_BACKEND_URL_VALUE}" ]]; then
  echo "ROUTING_BACKEND_URL: ${ROUTING_BACKEND_URL_VALUE}"
fi

cd "${ANDROID_DIR}"
GRADLE_ARGS=(clean assembleDebug)

if [[ -n "${MAP_BACKEND_URL_VALUE}" ]]; then
  GRADLE_ARGS+=("-Pdmap.backendUrl=${MAP_BACKEND_URL_VALUE}")
fi
if [[ -n "${SEARCH_BACKEND_URL_VALUE}" ]]; then
  GRADLE_ARGS+=("-Pdmap.searchBackendUrl=${SEARCH_BACKEND_URL_VALUE}")
fi
if [[ -n "${ROUTING_BACKEND_URL_VALUE}" ]]; then
  GRADLE_ARGS+=("-Pdmap.routingBackendUrl=${ROUTING_BACKEND_URL_VALUE}")
fi

JAVA_HOME="${JAVA_HOME_VALUE}" \
ANDROID_HOME="${SDK_DIR}" \
ANDROID_SDK_ROOT="${SDK_DIR}" \
./gradlew "${GRADLE_ARGS[@]}"

if [[ ! -f "${APK_PATH}" ]]; then
  echo "Build completed, but APK was not found at ${APK_PATH}." >&2
  exit 1
fi

echo
echo "APK ready:"
echo "${APK_PATH}"
