#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${INFRA_DIR}/.." && pwd)"

source "${SCRIPT_DIR}/load-env.sh"
dmap_load_env

require_path() {
  local path="$1"
  local description="$2"

  if [[ -e "${path}" ]]; then
    return 0
  fi

  echo "Missing ${description}: ${path}" >&2
  return 1
}

preflight_backend_assets() {
  local ok=0

  require_path "${INFRA_DIR}/tileserver/styles/osm-liberty/style.json" "TileServer style JSON" || ok=1
  require_path "${INFRA_DIR}/data/tiles/denmark.mbtiles" "MBTiles database" || ok=1
  require_path "${INFRA_DIR}/data/search/photon/photon.jar" "Photon jar" || ok=1
  require_path "${INFRA_DIR}/data/search/photon/photon_data" "Photon data directory" || ok=1
  require_path "${INFRA_DIR}/data/search/photon/.dataset-version" "Photon dataset marker" || ok=1

  if (( ok != 0 )); then
    echo >&2
    echo "Backend assets are not prepared in this worktree." >&2
    echo "Run ./infra/scripts/bootstrap-denmark.sh here, then retry ./infra/scripts/up-backend.sh." >&2
    exit 1
  fi
}

compose_args=(
  -f "${INFRA_DIR}/compose.yaml"
)

if [[ -f "${REPO_DIR}/.env" ]]; then
  compose_args=(
    --env-file "${REPO_DIR}/.env"
    "${compose_args[@]}"
  )
fi

preflight_backend_assets

"${SCRIPT_DIR}/sync-photon-volume.sh"

exec docker compose "${compose_args[@]}" up "$@"
