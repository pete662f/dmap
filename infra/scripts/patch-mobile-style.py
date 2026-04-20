#!/usr/bin/env python3
import json
import sys
from pathlib import Path


def label_fallback():
    return ["coalesce", ["get", "name"], ["get", "name_en"]]


def set_layer_property(style, layer_id, update_fn):
    for layer in style["layers"]:
        if layer.get("id") == layer_id:
            update_fn(layer)
            return
    raise KeyError(f"Missing style layer: {layer_id}")


def upsert_layer_before(style, layer, before_layer_ids):
    style["layers"] = [
        existing
        for existing in style["layers"]
        if existing.get("id") != layer["id"]
    ]

    before_ids = set(before_layer_ids)
    insert_at = next(
        (
            index
            for index, existing in enumerate(style["layers"])
            if existing.get("id") in before_ids
        ),
        len(style["layers"]),
    )
    style["layers"].insert(insert_at, layer)


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: patch-mobile-style.py /path/to/style.json", file=sys.stderr)
        return 1

    style_path = Path(sys.argv[1])
    style = json.loads(style_path.read_text())

    style.setdefault("metadata", {})
    style["metadata"]["dmap:style_variant"] = "mobile-m1"
    style.setdefault("sources", {})
    style["sources"]["dmap_poi_areas"] = {
        "type": "vector",
        "url": "mbtiles://{poi_areas}",
    }

    set_layer_property(style, "background", lambda layer: layer["paint"].update({
        "background-color": "rgb(242,239,233)",
    }))
    set_layer_property(style, "water", lambda layer: layer["paint"].update({
        "fill-color": "rgb(146,183,247)",
    }))
    set_layer_property(style, "park", lambda layer: layer["paint"].update({
        "fill-color": "#d9e6d2",
        "fill-opacity": 0.82,
        "fill-outline-color": "rgba(126, 171, 118, 0.55)",
    }))
    set_layer_property(style, "landuse_residential", lambda layer: layer["paint"].update({
        "fill-color": {
            "base": 1,
            "stops": [
                [9, "hsla(32, 26%, 89%, 0.78)"],
                [12, "hsla(34, 45%, 92%, 0.42)"],
            ],
        },
    }))

    def patch_place_city(layer):
        layer["layout"]["text-field"] = label_fallback()
        layer["layout"]["text-size"] = {"base": 1.15, "stops": [[7, 13], [11, 20]]}
        layer["paint"].update({
            "text-color": "#2f3338",
            "text-halo-color": "rgba(255,255,255,0.92)",
            "text-halo-width": 1.4,
        })

    def patch_place_town(layer):
        layer["minzoom"] = 7
        layer["layout"]["text-field"] = label_fallback()
        layer["layout"]["text-size"] = {"base": 1.1, "stops": [[7, 11], [11, 14]]}
        layer["paint"].update({
            "text-color": "#4c4f54",
            "text-halo-color": "rgba(255,255,255,0.88)",
            "text-halo-width": 1.2,
        })

    def patch_place_village(layer):
        layer["minzoom"] = 11
        layer["layout"]["text-field"] = label_fallback()
        layer["layout"]["text-size"] = {"base": 1.0, "stops": [[11, 10.5], [15, 15]]}
        layer["paint"].update({
            "text-color": "#62666b",
            "text-halo-color": "rgba(255,255,255,0.82)",
            "text-halo-width": 1.0,
        })

    set_layer_property(style, "place_city", patch_place_city)
    set_layer_property(style, "place_town", patch_place_town)
    set_layer_property(style, "place_village", patch_place_village)

    def patch_poi_transit(layer):
        layer["minzoom"] = 13
        layer["layout"]["text-field"] = label_fallback()
        layer["layout"]["text-font"] = ["Roboto Medium"]
        layer["layout"]["text-size"] = 11
        layer["paint"].update({
            "text-color": "#2f6fc6",
            "text-halo-color": "rgba(255,255,255,0.95)",
            "text-halo-width": 1.3,
        })

    def patch_poi_z14(layer):
        layer["minzoom"] = 13
        layer["filter"] = ["all", ["==", "$type", "Point"], [">=", "rank", 1], ["<", "rank", 4]]
        layer["layout"]["text-font"] = ["Roboto Medium"]
        layer["layout"]["text-size"] = 11.5
        layer["layout"]["text-max-width"] = 8
        layer["paint"].update({
            "text-color": "#4a4f56",
            "text-halo-color": "rgba(255,255,255,0.96)",
            "text-halo-width": 1.35,
            "text-halo-blur": 0.65,
        })

    def patch_poi_z15(layer):
        layer["minzoom"] = 14
        layer["filter"] = ["all", ["==", "$type", "Point"], [">=", "rank", 4], ["<", "rank", 12]]
        layer["layout"]["text-font"] = ["Roboto Regular"]
        layer["layout"]["text-size"] = 11
        layer["layout"]["text-max-width"] = 8
        layer["paint"].update({
            "text-color": "#5a6068",
            "text-halo-color": "rgba(255,255,255,0.94)",
            "text-halo-width": 1.25,
            "text-halo-blur": 0.55,
        })

    def patch_poi_z16(layer):
        layer["minzoom"] = 15
        layer["filter"] = ["all", ["==", "$type", "Point"], [">=", "rank", 12]]
        layer["layout"]["text-font"] = ["Roboto Regular"]
        layer["layout"]["text-size"] = 10.5
        layer["layout"]["text-max-width"] = 8
        layer["paint"].update({
            "text-color": "#676d74",
            "text-halo-color": "rgba(255,255,255,0.92)",
            "text-halo-width": 1.1,
            "text-halo-blur": 0.45,
        })

    set_layer_property(style, "poi_transit", patch_poi_transit)
    set_layer_property(style, "poi_z14", patch_poi_z14)
    set_layer_property(style, "poi_z15", patch_poi_z15)
    set_layer_property(style, "poi_z16", patch_poi_z16)

    upsert_layer_before(
        style,
        {
            "id": "dmap_poi_area_hitbox",
            "type": "fill",
            "source": "dmap_poi_areas",
            "source-layer": "poi_area",
            "minzoom": 12,
            "paint": {
                "fill-color": "#000000",
                "fill-opacity": 0.001,
            },
        },
        [
            "poi_transit",
            "poi_z14",
            "poi_z15",
            "poi_z16",
            "road_label",
            "place_village",
            "place_town",
            "place_city",
        ],
    )

    def patch_road_label(layer):
        layer["minzoom"] = 14
        layer["filter"] = [
            "all",
            ["!=", "class", "service"],
            ["!=", "class", "track"],
            ["!=", "class", "path"],
        ]
        layer["layout"]["text-size"] = {"base": 1, "stops": [[14, 11], [15.5, 12]]}
        layer["paint"].update({
            "text-color": "#7f7467",
            "text-halo-color": "rgba(255,255,255,0.95)",
            "text-halo-width": 1.1,
        })

    def patch_road_shield(layer):
        layer["minzoom"] = 8
        layer["layout"]["symbol-spacing"] = 650
        layer["layout"]["text-size"] = 9.5
        layer["layout"]["icon-size"] = 0.74

    set_layer_property(style, "road_label", patch_road_label)
    set_layer_property(style, "road_shield", patch_road_shield)

    style_path.write_text(json.dumps(style, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
