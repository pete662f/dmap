#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${INFRA_DIR}/.." && pwd)"

source "${SCRIPT_DIR}/load-env.sh"
dmap_load_env

exec docker compose \
  --env-file "${REPO_DIR}/.env" \
  -f "${INFRA_DIR}/compose.yaml" \
  up "$@"
