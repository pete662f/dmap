#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

source "${SCRIPT_DIR}/bootstrap-common.sh"
source "${INFRA_DIR}/versions.env"

CACHE_DIR="${INFRA_DIR}/.cache"
OSM_LIBERTY_DIR="${CACHE_DIR}/osm-liberty"
FONTS_RELEASE_ZIP="${CACHE_DIR}/openmaptiles-fonts-v2.0.zip"
FONTS_RELEASE_DIR="${CACHE_DIR}/openmaptiles-fonts-v2.0"
STYLE_OUT_DIR="${INFRA_DIR}/tileserver/styles/osm-liberty"
FONTS_OUT_DIR="${INFRA_DIR}/tileserver/fonts"
STYLE_MARKER_FILE="${STYLE_OUT_DIR}/.asset-version"
FORCE_REBUILD="${FORCE_REBUILD:-0}"

dmap_normalize_refresh_flags

clone_or_checkout() {
  local repo_url="$1"
  local repo_ref="$2"
  local dest_dir="$3"

  if [[ ! -d "${dest_dir}/.git" ]]; then
    rm -rf "${dest_dir}"
    git clone --filter=blob:none "${repo_url}" "${dest_dir}"
  fi

  if [[ "${REFRESH}" == "1" ]] || ! git -C "${dest_dir}" cat-file -e "${repo_ref}^{commit}" >/dev/null 2>&1; then
    git -C "${dest_dir}" fetch --depth 1 origin "${repo_ref}"
  fi
  git -C "${dest_dir}" checkout --detach "${repo_ref}"
}

mkdir -p "${CACHE_DIR}" "${STYLE_OUT_DIR}" "${FONTS_OUT_DIR}"

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

style_marker_content() {
  cat <<EOF
osm_liberty_ref=${OSM_LIBERTY_GIT_REF}
fonts_url=${OPENMAPTILES_FONTS_RELEASE_URL}
fonts_sha256=${OPENMAPTILES_FONTS_RELEASE_SHA256:-}
prepare_style_assets_sha256=$(dmap_sha256_file "${BASH_SOURCE[0]}")
patch_mobile_style_sha256=$(dmap_sha256_file "${SCRIPT_DIR}/patch-mobile-style.py")
EOF
}

style_outputs_exist() {
  local required_file
  for required_file in \
    "${STYLE_OUT_DIR}/style.json" \
    "${STYLE_OUT_DIR}/osm-liberty.json" \
    "${STYLE_OUT_DIR}/osm-liberty.png" \
    "${STYLE_OUT_DIR}/osm-liberty@2x.json" \
    "${STYLE_OUT_DIR}/osm-liberty@2x.png" \
    "${STYLE_OUT_DIR}/sprite.json" \
    "${STYLE_OUT_DIR}/sprite.png" \
    "${STYLE_OUT_DIR}/sprite@2x.json" \
    "${STYLE_OUT_DIR}/sprite@2x.png"
  do
    if [[ ! -s "${required_file}" ]]; then
      return 1
    fi
  done

  python3 - "${STYLE_OUT_DIR}/style.json" "${FONTS_OUT_DIR}" <<'PY'
import json
import sys
from pathlib import Path

style_path = Path(sys.argv[1])
fonts_out = Path(sys.argv[2])

try:
    style = json.loads(style_path.read_text())
except Exception:
    sys.exit(1)

fontstacks = set()
for layer in style.get("layers", []):
    text_font = layer.get("layout", {}).get("text-font")
    if isinstance(text_font, list) and text_font:
        fontstacks.add(", ".join(text_font))

if not fontstacks:
    sys.exit(1)

for fontstack in fontstacks:
    for glyph_range in ("0-255", "256-511"):
        if not (fonts_out / fontstack / f"{glyph_range}.pbf").is_file():
            sys.exit(1)
PY
}

STYLE_MARKER_CONTENT="$(style_marker_content)"

if [[ "${FORCE_REBUILD}" != "1" && "${REFRESH}" != "1" ]] &&
   [[ -f "${STYLE_MARKER_FILE}" ]] &&
   [[ "$(cat "${STYLE_MARKER_FILE}")" == "${STYLE_MARKER_CONTENT}" ]] &&
   style_outputs_exist; then
  echo "==> Reusing existing self-hosted style assets"
  exit 0
fi

echo "==> Cloning pinned OSM Liberty"
clone_or_checkout "https://github.com/maputnik/osm-liberty.git" "${OSM_LIBERTY_GIT_REF}" "${OSM_LIBERTY_DIR}"

echo "==> Copying OSM Liberty sprite assets"
cp "${OSM_LIBERTY_DIR}/sprites/osm-liberty.json" "${STYLE_OUT_DIR}/osm-liberty.json"
cp "${OSM_LIBERTY_DIR}/sprites/osm-liberty.png" "${STYLE_OUT_DIR}/osm-liberty.png"
cp "${OSM_LIBERTY_DIR}/sprites/osm-liberty@2x.json" "${STYLE_OUT_DIR}/osm-liberty@2x.json"
cp "${OSM_LIBERTY_DIR}/sprites/osm-liberty@2x.png" "${STYLE_OUT_DIR}/osm-liberty@2x.png"
cp "${OSM_LIBERTY_DIR}/sprites/osm-liberty.json" "${STYLE_OUT_DIR}/sprite.json"
cp "${OSM_LIBERTY_DIR}/sprites/osm-liberty.png" "${STYLE_OUT_DIR}/sprite.png"
cp "${OSM_LIBERTY_DIR}/sprites/osm-liberty@2x.json" "${STYLE_OUT_DIR}/sprite@2x.json"
cp "${OSM_LIBERTY_DIR}/sprites/osm-liberty@2x.png" "${STYLE_OUT_DIR}/sprite@2x.png"

echo "==> Rewriting style.json for self-hosted use"
python3 - "${OSM_LIBERTY_DIR}/style.json" "${STYLE_OUT_DIR}/style.json" <<'PY'
import json
import sys

source_path, output_path = sys.argv[1], sys.argv[2]
style = json.load(open(source_path))

style["sources"]["openmaptiles"]["url"] = "mbtiles://{openmaptiles}"
style["sprite"] = "{styleJsonFolder}/osm-liberty"
style["glyphs"] = "{fontstack}/{range}.pbf"
style["metadata"]["dmap:self_hosted"] = True
style["metadata"]["dmap:base_style"] = "osm-liberty"

style["sources"].pop("natural_earth_shaded_relief", None)
style["layers"] = [
    layer
    for layer in style["layers"]
    if layer.get("source") != "natural_earth_shaded_relief" and layer.get("id") != "natural_earth"
]

with open(output_path, "w", encoding="utf-8") as out:
    json.dump(style, out, indent=2)
    out.write("\n")
PY

echo "==> Applying deterministic mobile style tuning"
python3 "${SCRIPT_DIR}/patch-mobile-style.py" "${STYLE_OUT_DIR}/style.json"

rm -rf "${FONTS_OUT_DIR:?}"/*

if [[ "${REFRESH}" == "1" || ! -f "${FONTS_RELEASE_ZIP}" ]]; then
  echo "==> Downloading OpenMapTiles prebuilt fonts release"
  rm -f "${FONTS_RELEASE_ZIP}.tmp"
  curl -fsSL -o "${FONTS_RELEASE_ZIP}.tmp" "${OPENMAPTILES_FONTS_RELEASE_URL}"
  verify_sha256_if_configured "${FONTS_RELEASE_ZIP}.tmp" "${OPENMAPTILES_FONTS_RELEASE_SHA256:-}"
  mv "${FONTS_RELEASE_ZIP}.tmp" "${FONTS_RELEASE_ZIP}"
  rm -rf "${FONTS_RELEASE_DIR}"
fi
verify_sha256_if_configured "${FONTS_RELEASE_ZIP}" "${OPENMAPTILES_FONTS_RELEASE_SHA256:-}"

if [[ ! -d "${FONTS_RELEASE_DIR}" ]]; then
  mkdir -p "${FONTS_RELEASE_DIR}"
  unzip -q "${FONTS_RELEASE_ZIP}" -d "${FONTS_RELEASE_DIR}"
fi

echo "==> Prefetching glyph PBFs used by OSM Liberty"
python3 - "${STYLE_OUT_DIR}/style.json" "${FONTS_OUT_DIR}" "${FONTS_RELEASE_DIR}" <<'PY'
import json
import shutil
import sys
from pathlib import Path

style_path = Path(sys.argv[1])
fonts_out = Path(sys.argv[2])
fonts_release_dir = Path(sys.argv[3])

style = json.loads(style_path.read_text())
fontstacks = set()
for layer in style["layers"]:
    text_font = layer.get("layout", {}).get("text-font")
    if isinstance(text_font, list) and text_font:
        fontstacks.add(", ".join(text_font))

ranges = ["0-255", "256-511"]

for fontstack in sorted(fontstacks):
    target_dir = fonts_out / fontstack
    target_dir.mkdir(parents=True, exist_ok=True)
    for glyph_range in ranges:
        source = fonts_release_dir / fontstack / f"{glyph_range}.pbf"
        if not source.exists():
            raise FileNotFoundError(f"Missing glyph source {source}")
        target = target_dir / f"{glyph_range}.pbf"
        print(f"Copying {source} -> {target}")
        shutil.copy2(source, target)
PY

dmap_write_file_if_changed "${STYLE_MARKER_FILE}" "${STYLE_MARKER_CONTENT}"

echo "Style and font assets prepared in ${INFRA_DIR}/tileserver"
