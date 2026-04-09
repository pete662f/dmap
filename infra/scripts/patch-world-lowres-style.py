#!/usr/bin/env python3
import json
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: patch-world-lowres-style.py /path/to/style.json", file=sys.stderr)
        return 1

    style_path = Path(sys.argv[1])
    style = json.loads(style_path.read_text(encoding="utf-8"))

    style.setdefault("metadata", {})
    style["metadata"]["dmap:world_lowres"] = "natural-earth-vector"
    style["metadata"].pop("dmap:world_reference", None)
    for source_id in (
        "dmap-world-land",
        "dmap-world-borders",
        "dmap-world-cities",
    ):
        style["sources"].pop(source_id, None)
    style["sources"]["world-lowres"] = {
        "type": "vector",
        "url": "mbtiles://{world-lowres}",
    }

    style["layers"] = [
        layer for layer in style["layers"]
        if layer.get("id") not in {
            "dmap-world-water",
            "dmap-world-land",
            "dmap-world-country-borders",
            "dmap-world-country-labels",
            "dmap-world-major-cities",
        }
    ]

    world_reference_layers = [
        {
            "id": "dmap-world-water",
            "type": "fill",
            "source": "world-lowres",
            "source-layer": "world_water",
            "maxzoom": 7.0,
            "paint": {
                "fill-color": "rgb(146,183,247)",
                "fill-opacity": 1.0,
            },
        },
        {
            "id": "dmap-world-land",
            "type": "fill",
            "source": "world-lowres",
            "source-layer": "world_land",
            "maxzoom": 7.0,
            "paint": {
                "fill-color": "rgb(242,239,233)",
                "fill-opacity": 1.0,
            },
        },
        {
            "id": "dmap-world-country-borders",
            "type": "line",
            "source": "world-lowres",
            "source-layer": "world_boundaries",
            "maxzoom": 7.2,
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
    ]

    world_reference_label_layers = [
        {
            "id": "dmap-world-country-labels",
            "type": "symbol",
            "source": "world-lowres",
            "source-layer": "world_countries",
            "minzoom": 1.0,
            "maxzoom": 6.2,
            "layout": {
                "text-field": ["coalesce", ["get", "name_en"], ["get", "name"]],
                "text-font": ["Roboto Condensed Italic"],
                "text-max-width": 6.5,
                "text-size": {
                    "base": 1.1,
                    "stops": [[1, 10.5], [3, 12.5], [5, 15.5]],
                },
                "symbol-sort-key": ["get", "sort_rank"],
            },
            "paint": {
                "text-color": "#334",
                "text-halo-color": "rgba(255,255,255,0.82)",
                "text-halo-width": 1.0,
                "text-halo-blur": 0.9,
            },
        },
        {
            "id": "dmap-world-major-cities",
            "type": "symbol",
            "source": "world-lowres",
            "source-layer": "world_cities",
            "minzoom": 2.0,
            "maxzoom": 7.2,
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

    try:
        label_anchor_index = next(
            index for index, layer in enumerate(style["layers"])
            if layer.get("id") == "place_other"
        )
    except StopIteration:
        label_anchor_index = next(
            index for index, layer in enumerate(style["layers"])
            if layer.get("id") == "place_village"
        )

    style["layers"][label_anchor_index:label_anchor_index] = world_reference_label_layers

    style_path.write_text(json.dumps(style, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
