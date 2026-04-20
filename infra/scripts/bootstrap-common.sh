#!/usr/bin/env bash

dmap_normalize_refresh_flags() {
  REFRESH="${REFRESH:-0}"
  NO_REFRESH="${NO_REFRESH:-0}"

  if [[ "${REFRESH}" == "1" && "${NO_REFRESH}" == "1" ]]; then
    echo "REFRESH=1 and NO_REFRESH=1 conflict; choose one." >&2
    exit 1
  fi

  if [[ "${REFRESH}" != "0" && "${REFRESH}" != "1" ]]; then
    echo "REFRESH must be 0 or 1." >&2
    exit 1
  fi

  if [[ "${NO_REFRESH}" != "0" && "${NO_REFRESH}" != "1" ]]; then
    echo "NO_REFRESH must be 0 or 1." >&2
    exit 1
  fi

  export REFRESH
}

dmap_sha256_file() {
  local file="$1"

  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${file}" | awk '{print $1}'
  else
    sha256sum "${file}" | awk '{print $1}'
  fi
}

dmap_write_file_if_changed() {
  local path="$1"
  local content="$2"
  local dir
  local tmp_file

  dir="$(dirname "${path}")"
  mkdir -p "${dir}"
  tmp_file="$(mktemp "${dir}/.tmp.$(basename "${path}").XXXXXX")"
  printf '%s\n' "${content}" > "${tmp_file}"

  if [[ -f "${path}" ]] && cmp -s "${tmp_file}" "${path}"; then
    rm -f "${tmp_file}"
    return
  fi

  mv "${tmp_file}" "${path}"
}
