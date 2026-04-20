#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class AreaClassification:
    feature_class: str
    subclass: str
    category: str


AMENITY_CLASS_MAP = {
    "parking": AreaClassification("parking", "parking", "Parking"),
    "bicycle_parking": AreaClassification("bicycle_parking", "bicycle_parking", "Bicycle Parking"),
    "motorcycle_parking": AreaClassification("motorcycle_parking", "motorcycle_parking", "Motorcycle Parking"),
    "school": AreaClassification("school", "school", "School"),
    "kindergarten": AreaClassification("school", "kindergarten", "Kindergarten"),
    "university": AreaClassification("college", "university", "University"),
    "college": AreaClassification("college", "college", "College"),
    "hospital": AreaClassification("hospital", "hospital", "Hospital"),
    "clinic": AreaClassification("hospital", "clinic", "Clinic"),
    "grave_yard": AreaClassification("cemetery", "grave_yard", "Cemetery"),
    "fuel": AreaClassification("fuel", "fuel", "Fuel"),
    "charging_station": AreaClassification("charging_station", "charging_station", "Charging Station"),
    "marketplace": AreaClassification("marketplace", "marketplace", "Marketplace"),
}

LEISURE_CLASS_MAP = {
    "park": AreaClassification("park", "park", "Park"),
    "pitch": AreaClassification("pitch", "pitch", "Pitch"),
    "track": AreaClassification("track", "track", "Track"),
    "playground": AreaClassification("playground", "playground", "Playground"),
    "sports_centre": AreaClassification("sports_centre", "sports_centre", "Sports Centre"),
    "stadium": AreaClassification("stadium", "stadium", "Stadium"),
}

TOURISM_CLASS_MAP = {
    "attraction": AreaClassification("attraction", "attraction", "Attraction"),
    "zoo": AreaClassification("zoo", "zoo", "Zoo"),
    "theme_park": AreaClassification("theme_park", "theme_park", "Theme Park"),
}

LANDUSE_CLASS_MAP = {
    "cemetery": AreaClassification("cemetery", "cemetery", "Cemetery"),
    "grass": AreaClassification("grass", "grass", "Grass"),
    "recreation_ground": AreaClassification("recreation_ground", "recreation_ground", "Recreation Ground"),
}

EXCLUDED_LANDUSE = {"residential"}


def classify_area(tags: dict[str, str]) -> AreaClassification | None:
    if "boundary" in tags:
        return None
    if tags.get("landuse") in EXCLUDED_LANDUSE:
        return None

    amenity = tags.get("amenity")
    if amenity in AMENITY_CLASS_MAP:
        return AMENITY_CLASS_MAP[amenity]

    leisure = tags.get("leisure")
    if leisure in LEISURE_CLASS_MAP:
        return LEISURE_CLASS_MAP[leisure]

    tourism = tags.get("tourism")
    if tourism in TOURISM_CLASS_MAP:
        return TOURISM_CLASS_MAP[tourism]

    landuse = tags.get("landuse")
    if landuse in LANDUSE_CLASS_MAP:
        return LANDUSE_CLASS_MAP[landuse]

    shop = tags.get("shop")
    if shop:
        return AreaClassification("shop", shop, format_label(shop))

    office = tags.get("office")
    if office and first_non_blank(tags.get("name"), tags.get("name:da"), tags.get("name:en")):
        return AreaClassification("office", office, format_label(office))

    if "building" in tags:
        return None

    return None


def format_label(raw: str) -> str:
    return " ".join(part.capitalize() for part in raw.replace("-", "_").split("_") if part)


def first_non_blank(*values: str | None) -> str | None:
    for value in values:
        if value and value.strip():
            return value.strip()
    return None


def tag_dict(tags: Iterable[object]) -> dict[str, str]:
    return {tag.k: tag.v for tag in tags}


def location_to_lon_lat(node: object) -> tuple[float, float] | None:
    location = getattr(node, "location", None)
    if location is not None:
        valid = getattr(location, "valid", None)
        if callable(valid) and not valid():
            return None
        return float(location.lon), float(location.lat)

    lon = getattr(node, "lon", None)
    lat = getattr(node, "lat", None)
    if lon is None or lat is None:
        return None
    return float(lon), float(lat)


def ring_coordinates(ring: Iterable[object]) -> list[list[float]]:
    coordinates: list[list[float]] = []
    for node in ring:
        lon_lat = location_to_lon_lat(node)
        if lon_lat is None:
            continue
        coordinates.append([lon_lat[0], lon_lat[1]])

    if len(coordinates) < 3:
        return []
    if coordinates[0] != coordinates[-1]:
        coordinates.append(coordinates[0])
    if len(coordinates) < 4:
        return []
    return coordinates


def polygon_area_m2(rings: list[list[list[float]]]) -> float:
    if not rings:
        return 0.0

    def ring_area(ring: list[list[float]]) -> float:
        if len(ring) < 4:
            return 0.0
        mean_lat = math.radians(sum(point[1] for point in ring) / len(ring))
        meters_per_degree_lat = 111_320.0
        meters_per_degree_lon = 111_320.0 * math.cos(mean_lat)
        points = [
            (point[0] * meters_per_degree_lon, point[1] * meters_per_degree_lat)
            for point in ring
        ]
        twice_area = 0.0
        for index in range(len(points) - 1):
            x1, y1 = points[index]
            x2, y2 = points[index + 1]
            twice_area += x1 * y2 - x2 * y1
        return abs(twice_area) / 2.0

    outer = ring_area(rings[0])
    holes = sum(ring_area(ring) for ring in rings[1:])
    return max(outer - holes, 0.0)


def feature_from_area(area: object) -> dict[str, object] | None:
    tags = tag_dict(area.tags)
    classification = classify_area(tags)
    if classification is None:
        return None

    polygons: list[list[list[list[float]]]] = []
    for outer in area.outer_rings():
        outer_coordinates = ring_coordinates(outer)
        if not outer_coordinates:
            continue

        rings = [outer_coordinates]
        for inner in area.inner_rings(outer):
            inner_coordinates = ring_coordinates(inner)
            if inner_coordinates:
                rings.append(inner_coordinates)
        polygons.append(rings)

    if not polygons:
        return None

    if len(polygons) == 1:
        geometry = {
            "type": "Polygon",
            "coordinates": polygons[0],
        }
    else:
        geometry = {
            "type": "MultiPolygon",
            "coordinates": polygons,
        }

    osm_type = "way" if area.from_way() else "relation"
    osm_id = int(area.orig_id())
    properties = {
        "osm_type": osm_type,
        "osm_id": osm_id,
        "class": classification.feature_class,
        "subclass": classification.subclass,
        "category": classification.category,
        "area_m2": round(sum(polygon_area_m2(polygon) for polygon in polygons), 2),
    }

    for source_key, output_key in (
        ("name", "name"),
        ("name:da", "name_da"),
        ("name:en", "name_en"),
    ):
        value = first_non_blank(tags.get(source_key))
        if value:
            properties[output_key] = value

    return {
        "type": "Feature",
        "id": f"{osm_type}/{osm_id}",
        "properties": properties,
        "geometry": geometry,
    }


def extract(input_path: Path, output_path: Path) -> int:
    import osmium

    class PoiAreaHandler(osmium.SimpleHandler):
        def __init__(self, out_file):
            super().__init__()
            self.out_file = out_file
            self.count = 0

        def area(self, area):
            feature = feature_from_area(area)
            if feature is None:
                return
            self.out_file.write(json.dumps(feature, separators=(",", ":"), ensure_ascii=False))
            self.out_file.write("\n")
            self.count += 1

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as out:
        handler = PoiAreaHandler(out)
        handler.apply_file(str(input_path), locations=True)
        return handler.count


def run_self_test() -> int:
    cases = [
        ({"amenity": "parking"}, AreaClassification("parking", "parking", "Parking")),
        ({"amenity": "bicycle_parking"}, AreaClassification("bicycle_parking", "bicycle_parking", "Bicycle Parking")),
        ({"amenity": "school"}, AreaClassification("school", "school", "School")),
        ({"amenity": "school", "building": "school"}, AreaClassification("school", "school", "School")),
        ({"leisure": "park"}, AreaClassification("park", "park", "Park")),
        ({"landuse": "residential"}, None),
        ({"building": "yes"}, None),
    ]
    for tags, expected in cases:
        actual = classify_area(tags)
        if actual != expected:
            print(f"self-test failed for {tags}: expected {expected}, got {actual}", file=sys.stderr)
            return 1
    print("extract_poi_area_geojson.py self-test passed")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", nargs="?")
    parser.add_argument("output", nargs="?")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        return run_self_test()

    if not args.input or not args.output:
        parser.error("input and output are required unless --self-test is used")

    input_path = Path(args.input)
    output_path = Path(args.output)
    count = extract(input_path, output_path)
    print(f"Wrote {count} POI area features to {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
