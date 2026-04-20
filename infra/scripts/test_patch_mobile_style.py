#!/usr/bin/env python3
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("patch-mobile-style.py")


class PatchMobileStyleTest(unittest.TestCase):
    def test_house_number_layer_is_added_once_before_pois(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            style_path = Path(temp_dir) / "style.json"
            style_path.write_text(json.dumps(minimal_style()), encoding="utf-8")

            run_patcher(style_path)
            run_patcher(style_path)

            style = json.loads(style_path.read_text())
            house_number_layers = [
                layer
                for layer in style["layers"]
                if layer.get("id") == "dmap_house_number"
            ]

            self.assertEqual(len(house_number_layers), 1)

            house_number_layer = house_number_layers[0]
            self.assertEqual(house_number_layer["type"], "symbol")
            self.assertEqual(house_number_layer["source"], "openmaptiles")
            self.assertEqual(house_number_layer["source-layer"], "housenumber")
            self.assertEqual(house_number_layer["minzoom"], 17)
            self.assertEqual(
                house_number_layer["layout"]["text-field"],
                ["get", "housenumber"],
            )

            layer_ids = [layer["id"] for layer in style["layers"]]
            self.assertLess(
                layer_ids.index("dmap_house_number"),
                layer_ids.index("poi_z16"),
            )


def run_patcher(style_path):
    subprocess.run(
        [sys.executable, str(SCRIPT_PATH), str(style_path)],
        check=True,
        text=True,
        capture_output=True,
    )


def minimal_style():
    return {
        "version": 8,
        "name": "test",
        "sources": {
            "openmaptiles": {
                "type": "vector",
                "url": "mbtiles://{openmaptiles}",
            },
        },
        "layers": [
            paint_layer("background", "background"),
            paint_layer("water", "fill"),
            paint_layer("park", "fill"),
            paint_layer("landuse_residential", "fill"),
            symbol_layer("place_city"),
            symbol_layer("place_town"),
            symbol_layer("place_village"),
            symbol_layer("poi_transit"),
            symbol_layer("poi_z14"),
            symbol_layer("poi_z15"),
            symbol_layer("poi_z16"),
            symbol_layer("road_label"),
            symbol_layer("road_shield"),
        ],
    }


def paint_layer(layer_id, layer_type):
    return {
        "id": layer_id,
        "type": layer_type,
        "paint": {},
    }


def symbol_layer(layer_id):
    return {
        "id": layer_id,
        "type": "symbol",
        "layout": {},
        "paint": {},
    }


if __name__ == "__main__":
    unittest.main()
