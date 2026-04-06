#!/usr/bin/env bash
set -euo pipefail

PHOTON_HOME="${PHOTON_HOME:-/srv/photon}"
PHOTON_JAR="${PHOTON_JAR:-${PHOTON_HOME}/photon.jar}"
PHOTON_DATA_DIR="${PHOTON_DATA_DIR:-${PHOTON_HOME}}"
PHOTON_LISTEN_IP="${PHOTON_LISTEN_IP:-0.0.0.0}"
PHOTON_LISTEN_PORT="${PHOTON_LISTEN_PORT:-2322}"
PHOTON_DEFAULT_LANGUAGE="${PHOTON_DEFAULT_LANGUAGE:-da}"
PHOTON_MAX_RESULTS="${PHOTON_MAX_RESULTS:-8}"
PHOTON_MAX_REVERSE_RESULTS="${PHOTON_MAX_REVERSE_RESULTS:-3}"
PHOTON_QUERY_TIMEOUT="${PHOTON_QUERY_TIMEOUT:-4}"
PHOTON_JAVA_XMX="${PHOTON_JAVA_XMX:-2g}"
PHOTON_CORS_MODE="${PHOTON_CORS_MODE:-any}"

if [[ ! -f "${PHOTON_JAR}" ]]; then
  echo "Photon jar not found at ${PHOTON_JAR}" >&2
  exit 1
fi

if [[ ! -d "${PHOTON_DATA_DIR}/photon_data" ]]; then
  echo "Photon data directory not found at ${PHOTON_DATA_DIR}/photon_data" >&2
  exit 1
fi

cors_args=()
if [[ "${PHOTON_CORS_MODE}" == "any" ]]; then
  cors_args+=(-cors-any)
elif [[ -n "${PHOTON_CORS_MODE}" ]]; then
  cors_args+=(-cors-origin "${PHOTON_CORS_MODE}")
fi

exec java \
  -Xmx"${PHOTON_JAVA_XMX}" \
  -jar "${PHOTON_JAR}" \
  -data-dir "${PHOTON_DATA_DIR}" \
  serve \
  -listen-ip "${PHOTON_LISTEN_IP}" \
  -listen-port "${PHOTON_LISTEN_PORT}" \
  -default-language "${PHOTON_DEFAULT_LANGUAGE}" \
  -max-results "${PHOTON_MAX_RESULTS}" \
  -max-reverse-results "${PHOTON_MAX_REVERSE_RESULTS}" \
  -query-timeout "${PHOTON_QUERY_TIMEOUT}" \
  "${cors_args[@]}"
