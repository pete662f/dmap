This directory is populated by `../../scripts/prepare-style-assets.sh`.

The generated style is based on a pinned upstream OSM Liberty snapshot and then passed through `../../scripts/patch-mobile-style.py` to keep the Android mobile presentation deterministic and maintainable.

Generated outputs:

- `style.json`
- `sprite.json`
- `sprite.png`
- `sprite@2x.json`
- `sprite@2x.png`
- `osm-liberty.json`
- `osm-liberty.png`
- `osm-liberty@2x.json`
- `osm-liberty@2x.png`

The generated style is based on OSM Liberty but rewritten for fully self-hosted use:

- vector source -> `mbtiles://{openmaptiles}`
- sprite -> local TileServer style folder
- glyphs -> local TileServer fonts endpoint
- external natural-earth raster source removed

The matching glyph PBFs are prefetched into `../../fonts/` during bootstrap.
