#!/usr/bin/env python3
import json
import sys
from pathlib import Path


WORLD_REFERENCE_SOURCE_PREFIX = "dmap-world"


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: patch-world-reference-style.py /path/to/style.json", file=sys.stderr)
        return 1

    style_path = Path(sys.argv[1])
    style = json.loads(style_path.read_text(encoding="utf-8"))

    style.setdefault("metadata", {})
    style["metadata"]["dmap:world_reference"] = "natural-earth"

    style["sources"][f"{WORLD_REFERENCE_SOURCE_PREFIX}-land"] = {
        "type": "geojson",
        "data": "file://world-reference/land.geojson",
    }
    style["sources"][f"{WORLD_REFERENCE_SOURCE_PREFIX}-borders"] = {
        "type": "geojson",
        "data": "file://world-reference/country-borders.geojson",
    }
    style["sources"][f"{WORLD_REFERENCE_SOURCE_PREFIX}-cities"] = {
        "type": "geojson",
        "data": "file://world-reference/major-cities.geojson",
    }

    for layer in style["layers"]:
        if layer.get("id") == "background":
            layer.setdefault("paint", {})
            layer["paint"]["background-color"] = "rgb(146,183,247)"
            break
    else:
        raise KeyError("Missing style layer: background")

    world_reference_layers = [
        {
            "id": "dmap-world-land",
            "type": "fill",
            "source": f"{WORLD_REFERENCE_SOURCE_PREFIX}-land",
            "paint": {
                "fill-color": "rgb(242,239,233)",
                "fill-opacity": 1.0,
            },
        },
        {
            "id": "dmap-world-country-borders",
            "type": "line",
            "source": f"{WORLD_REFERENCE_SOURCE_PREFIX}-borders",
            "layout": {
                "line-join": "round",
                "line-cap": "round",
            },
            "paint": {
                "line-color": "rgba(125, 119, 112, 0.42)",
                "line-width": {
                    "base": 1.1,
                    "stops": [[0, 0.45], [5, 0.9], [8, 1.25]],
                },
            },
        },
        {
            "id": "dmap-world-major-cities",
            "type": "symbol",
            "source": f"{WORLD_REFERENCE_SOURCE_PREFIX}-cities",
            "minzoom": 2,
            "maxzoom": 8.5,
            "layout": {
                "text-field": ["coalesce", ["get", "name"], ["get", "name_en"]],
                "text-font": ["Roboto Medium"],
                "text-size": {
                    "base": 1.15,
                    "stops": [[2, 10.5], [4, 12.0], [7, 14.5]],
                },
                "text-letter-spacing": 0.02,
                "text-max-width": 7,
                "text-offset": [0, 0.2],
                "text-anchor": "center",
                "symbol-sort-key": ["get", "sort_rank"],
            },
            "paint": {
                "text-color": "#495057",
                "text-halo-color": "rgba(255,255,255,0.92)",
                "text-halo-width": 1.1,
                "text-halo-blur": 0.6,
            },
        },
    ]

    background_index = next(
        index for index, layer in enumerate(style["layers"])
        if layer.get("id") == "background"
    )
    style["layers"][background_index + 1:background_index + 1] = world_reference_layers

    style_path.write_text(json.dumps(style, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
