#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "${SCRIPT_DIR}/load-env.sh"
dmap_load_env

MAP_BASE_URL="$(dmap_resolve_bound_url 8080 localhost)"
SEARCH_BASE_URL="$(dmap_resolve_bound_url 8081 localhost)"
PHOTON_CONTAINER="${PHOTON_CONTAINER:-dmap2-photon}"
REQUESTS="${REQUESTS:-40}"
SEARCH_CONCURRENCIES="${SEARCH_CONCURRENCIES:-1,4,8}"
MODE="${MODE:-all}"

usage() {
  cat <<'EOF'
Usage: ./infra/scripts/benchmark-backend.sh [options]

Options:
  --mode warm|cold|all        Which benchmark set to run. Default: all
  --requests N                Number of warm requests per scenario. Default: 40
  --concurrency LIST          Comma-separated warm search concurrencies. Default: 1,4,8
  --search-url URL            Override the search backend base URL
  --map-url URL               Override the map backend base URL
  --photon-container NAME     Override the Photon container name
  --help                      Show this help text
EOF
}

require_value() {
  local flag="$1"
  local value="${2:-}"

  if [[ -z "${value}" || "${value}" == --* ]]; then
    echo "Missing value for ${flag}" >&2
    usage >&2
    exit 1
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      require_value "$1" "${2:-}"
      MODE="$2"
      shift 2
      ;;
    --requests)
      require_value "$1" "${2:-}"
      REQUESTS="$2"
      shift 2
      ;;
    --concurrency)
      require_value "$1" "${2:-}"
      SEARCH_CONCURRENCIES="$2"
      shift 2
      ;;
    --search-url)
      require_value "$1" "${2:-}"
      SEARCH_BASE_URL="$2"
      shift 2
      ;;
    --map-url)
      require_value "$1" "${2:-}"
      MAP_BASE_URL="$2"
      shift 2
      ;;
    --photon-container)
      require_value "$1" "${2:-}"
      PHOTON_CONTAINER="$2"
      shift 2
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! "${REQUESTS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "Requests must be a positive integer." >&2
  exit 1
fi

case "${MODE}" in
  warm|cold|all)
    ;;
  *)
    echo "Mode must be one of: warm, cold, all." >&2
    exit 1
    ;;
esac

curl_time() {
  local url="$1"
  curl --silent --show-error --location --output /dev/null --write-out '%{time_total}\n' "${url}"
}

wait_for_status() {
  local url="$1"
  local max_attempts="${2:-60}"
  local attempt=1

  while (( attempt <= max_attempts )); do
    if curl --silent --show-error --fail --location --output /dev/null "${url}"; then
      return 0
    fi
    sleep 1
    ((attempt++))
  done

  echo "Timed out waiting for ${url}" >&2
  exit 1
}

run_series() {
  local label="$1"
  local url="$2"
  local requests="$3"
  local concurrency="$4"
  local summary

  summary="$(seq 1 "${requests}" | xargs -I{} -P "${concurrency}" \
    sh -c 'curl --silent --show-error --location --output /dev/null --write-out "%{time_total}\n" "$1"' _ "${url}" | \
    awk '
      {
        sum += $1
        if (min == 0 || $1 < min) min = $1
        if ($1 > max) max = $1
        count += 1
      }
      END {
        if (count == 0) exit 1
        printf "count=%d avg=%.6f min=%.6f max=%.6f", count, sum / count, min, max
      }
    ')"

  printf "%s\n" "==> ${label}"
  printf "url=%s concurrency=%s %s\n" "${url}" "${concurrency}" "${summary}"
  printf "\n"
}

run_warm_benchmarks() {
  local status_url="${SEARCH_BASE_URL}/status"
  local search_url="${SEARCH_BASE_URL}/api?q=aarhus&limit=3"
  local reverse_url="${SEARCH_BASE_URL}/reverse?lon=12.5683&lat=55.6761&limit=1"
  local tile_url="${MAP_BASE_URL}/data/openmaptiles/0/0/0.pbf"
  local concurrency

  wait_for_status "${status_url}"

  run_series "Photon status warm" "${status_url}" "${REQUESTS}" 1

  IFS=',' read -r -a concurrency_values <<< "${SEARCH_CONCURRENCIES}"
  for concurrency in "${concurrency_values[@]}"; do
    if [[ ! "${concurrency}" =~ ^[1-9][0-9]*$ ]]; then
      echo "Each concurrency value must be a positive integer." >&2
      exit 1
    fi
    run_series "Photon search warm" "${search_url}" "${REQUESTS}" "${concurrency}"
  done

  run_series "Photon reverse warm" "${reverse_url}" "${REQUESTS}" 1
  run_series "Tile warm" "${tile_url}" "${REQUESTS}" 1
}

run_cold_benchmark() {
  local status_url="${SEARCH_BASE_URL}/status"
  local search_url="${SEARCH_BASE_URL}/api?q=aarhus&limit=3"
  local first_query_time

  printf "%s\n" "==> Photon search cold"
  docker restart "${PHOTON_CONTAINER}" >/dev/null
  wait_for_status "${status_url}"
  first_query_time="$(curl_time "${search_url}")"
  printf "url=%s count=1 avg=%s min=%s max=%s first_query=%s\n" \
    "${search_url}" "${first_query_time}" "${first_query_time}" "${first_query_time}" "${first_query_time}"
  printf "\n"
}

printf "%s\n" "Benchmark configuration"
printf "map_base_url=%s\n" "${MAP_BASE_URL}"
printf "search_base_url=%s\n" "${SEARCH_BASE_URL}"
printf "requests=%s\n" "${REQUESTS}"
printf "search_concurrencies=%s\n" "${SEARCH_CONCURRENCIES}"
printf "mode=%s\n" "${MODE}"
printf "\n"

case "${MODE}" in
  warm)
    run_warm_benchmarks
    ;;
  cold)
    run_cold_benchmark
    ;;
  all)
    run_warm_benchmarks
    run_cold_benchmark
    ;;
esac
