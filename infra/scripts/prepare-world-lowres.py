#!/usr/bin/env python3
import json
import shutil
import sys
import urllib.request
from pathlib import Path


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


def compact_feature(feature: dict, properties: dict | None = None) -> dict:
    return {
        "type": "Feature",
        "properties": properties or {},
        "geometry": feature["geometry"],
    }


def write_geojson(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def prepare_land(land_geojson: dict) -> dict:
    return {
        "type": "FeatureCollection",
        "features": [compact_feature(feature) for feature in land_geojson["features"]],
    }


def prepare_water(water_geojson: dict) -> dict:
    return {
        "type": "FeatureCollection",
        "features": [compact_feature(feature) for feature in water_geojson["features"]],
    }


def prepare_boundaries(boundary_geojson: dict) -> dict:
    features = []
    for feature in boundary_geojson["features"]:
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


def prepare_country_labels(country_geojson: dict) -> dict:
    features = []
    for feature in country_geojson["features"]:
        properties = feature.get("properties", {})
        label_x = properties.get("LABEL_X")
        label_y = properties.get("LABEL_Y")
        if label_x is None or label_y is None:
            continue

        label_rank = properties.get("LABELRANK", 99)
        scale_rank = properties.get("scalerank", 99)
        min_zoom = properties.get("MIN_LABEL", 1.0)

        features.append(
            {
                "type": "Feature",
                "properties": {
                    "name": properties.get("NAME"),
                    "name_en": properties.get("NAME_EN") or properties.get("NAME"),
                    "labelrank": label_rank,
                    "scalerank": scale_rank,
                    "min_zoom": min_zoom,
                    "sort_rank": label_rank * 100 + scale_rank * 10,
                },
                "geometry": {
                    "type": "Point",
                    "coordinates": [label_x, label_y],
                },
            },
        )

    features.sort(
        key=lambda feature: (
            feature["properties"]["labelrank"],
            feature["properties"]["scalerank"],
            feature["properties"]["name_en"] or "",
        ),
    )
    return {"type": "FeatureCollection", "features": features}


def prepare_city_labels(city_geojson: dict) -> dict:
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


def main() -> int:
    if len(sys.argv) != 4:
        print(
            "Usage: prepare-world-lowres.py NATURAL_EARTH_REF CACHE_DIR OUTPUT_DIR",
            file=sys.stderr,
        )
        return 1

    natural_earth_ref = sys.argv[1]
    cache_dir = Path(sys.argv[2])
    output_dir = Path(sys.argv[3])
    output_dir.mkdir(parents=True, exist_ok=True)

    land_geojson = load_geojson(cache_dir, natural_earth_ref, "ne_110m_land.geojson")
    water_geojson = load_geojson(cache_dir, natural_earth_ref, "ne_110m_ocean.geojson")
    boundary_geojson = load_geojson(cache_dir, natural_earth_ref, "ne_110m_admin_0_boundary_lines_land.geojson")
    city_geojson = load_geojson(cache_dir, natural_earth_ref, "ne_50m_populated_places_simple.geojson")
    country_geojson = load_geojson(cache_dir, natural_earth_ref, "ne_50m_admin_0_countries.geojson")

    write_geojson(output_dir / "world_land.geojson", prepare_land(land_geojson))
    write_geojson(output_dir / "world_water.geojson", prepare_water(water_geojson))
    write_geojson(output_dir / "world_boundaries.geojson", prepare_boundaries(boundary_geojson))
    write_geojson(output_dir / "world_countries.geojson", prepare_country_labels(country_geojson))
    write_geojson(output_dir / "world_cities.geojson", prepare_city_labels(city_geojson))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
