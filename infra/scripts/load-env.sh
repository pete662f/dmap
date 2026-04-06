#!/usr/bin/env bash

if [[ -n "${DMAP_ENV_HELPERS_LOADED:-}" ]]; then
  return 0
fi
DMAP_ENV_HELPERS_LOADED=1

dmap_repo_dir() {
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  cd "${script_dir}/../.." && pwd
}

dmap_env_file() {
  printf '%s/.env\n' "$(dmap_repo_dir)"
}

dmap_load_env() {
  local env_file
  env_file="$(dmap_env_file)"
  if [[ ! -f "${env_file}" ]]; then
    return 0
  fi

  while IFS= read -r raw_line || [[ -n "${raw_line}" ]]; do
    local line key value first last
    line="${raw_line#"${raw_line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"

    if [[ -z "${line}" || "${line}" == \#* ]]; then
      continue
    fi

    if [[ "${line}" != *=* ]]; then
      continue
    fi

    key="${line%%=*}"
    value="${line#*=}"
    key="${key%"${key##*[![:space:]]}"}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"

    if [[ ${#value} -ge 2 ]]; then
      first="${value:0:1}"
      last="${value: -1}"
      if [[ ( "${first}" == '"' && "${last}" == '"' ) || ( "${first}" == "'" && "${last}" == "'" ) ]]; then
        value="${value:1:${#value}-2}"
      fi
    fi

    if [[ -z "${!key+x}" ]]; then
      export "${key}=${value}"
    fi
  done < "${env_file}"
}

dmap_resolve_url() {
  local explicit_var="$1"
  local port="$2"
  local fallback_host="${3:-localhost}"
  local explicit_value="${!explicit_var:-}"

  if [[ -n "${explicit_value}" ]]; then
    printf '%s\n' "${explicit_value}"
    return
  fi

  if [[ -n "${DMAP_HOST_IP:-}" ]]; then
    printf 'http://%s:%s\n' "${DMAP_HOST_IP}" "${port}"
    return
  fi

  printf 'http://%s:%s\n' "${fallback_host}" "${port}"
}
