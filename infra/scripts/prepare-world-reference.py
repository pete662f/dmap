#!/usr/bin/env python3
import json
import shutil
import sys
import urllib.request
from pathlib import Path
from typing import Optional


WORLD_CITY_LIMIT = 180


def load_geojson(cache_dir: Path, ref: str, filename: str) -> dict:
    ref_cache_dir = cache_dir / ref
    ref_cache_dir.mkdir(parents=True, exist_ok=True)
    cached_path = ref_cache_dir / filename
    if not cached_path.exists():
        url = f"https://raw.githubusercontent.com/nvkelso/natural-earth-vector/{ref}/geojson/{filename}"
        with urllib.request.urlopen(url) as response, cached_path.open("wb") as output:
            shutil.copyfileobj(response, output)
    return json.loads(cached_path.read_text(encoding="utf-8"))


def compact_feature(feature: dict, properties: Optional[dict] = None) -> dict:
    return {
        "type": "Feature",
        "properties": properties or {},
        "geometry": feature["geometry"],
    }


def prepare_land(land_geojson: dict) -> dict:
    return {
        "type": "FeatureCollection",
        "features": [compact_feature(feature) for feature in land_geojson["features"]],
    }


def prepare_borders(border_geojson: dict) -> dict:
    features = []
    for feature in border_geojson["features"]:
        properties = feature.get("properties", {})
        features.append(
            compact_feature(
                feature,
                properties={
                    "name": properties.get("NAME"),
                    "min_zoom": properties.get("MIN_ZOOM", 0),
                    "scalerank": properties.get("SCALERANK", 0),
                },
            ),
        )
    return {"type": "FeatureCollection", "features": features}


def prepare_cities(city_geojson: dict) -> dict:
    selected = []
    for feature in city_geojson["features"]:
        properties = feature.get("properties", {})
        min_zoom = properties.get("min_zoom")
        if min_zoom is None or min_zoom > 4.7:
            continue

        selected.append(
            compact_feature(
                feature,
                properties={
                    "name": properties.get("name") or properties.get("nameascii"),
                    "name_en": properties.get("nameascii") or properties.get("name"),
                    "min_zoom": min_zoom,
                    "labelrank": properties.get("labelrank", 99),
                    "scalerank": properties.get("scalerank", 99),
                    "pop_max": properties.get("pop_max", 0),
                    "sort_rank": (
                        properties.get("labelrank", 99) * 100 +
                        properties.get("scalerank", 99) * 10
                    ),
                },
            ),
        )

    selected.sort(
        key=lambda feature: (
            feature["properties"]["min_zoom"],
            feature["properties"]["labelrank"],
            feature["properties"]["scalerank"],
            -feature["properties"]["pop_max"],
            feature["properties"]["name_en"] or "",
        ),
    )

    return {
        "type": "FeatureCollection",
        "features": selected[:WORLD_CITY_LIMIT],
    }


def write_geojson(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    if len(sys.argv) != 4:
        print(
            "Usage: prepare-world-reference.py NATURAL_EARTH_REF CACHE_DIR OUTPUT_DIR",
            file=sys.stderr,
        )
        return 1

    natural_earth_ref = sys.argv[1]
    cache_dir = Path(sys.argv[2])
    output_dir = Path(sys.argv[3])
    output_dir.mkdir(parents=True, exist_ok=True)

    land_geojson = load_geojson(cache_dir, natural_earth_ref, "ne_110m_land.geojson")
    border_geojson = load_geojson(cache_dir, natural_earth_ref, "ne_110m_admin_0_boundary_lines_land.geojson")
    city_geojson = load_geojson(cache_dir, natural_earth_ref, "ne_50m_populated_places_simple.geojson")

    write_geojson(output_dir / "land.geojson", prepare_land(land_geojson))
    write_geojson(output_dir / "country-borders.geojson", prepare_borders(border_geojson))
    write_geojson(output_dir / "major-cities.geojson", prepare_cities(city_geojson))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
