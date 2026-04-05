# Architecture

## Monorepo

```text
android/  native app
infra/    local tile infrastructure and bootstrap scripts
docs/     setup, architecture, and roadmap
```

## M1 backend

```text
OSM Denmark extract
  -> Planetiler OpenMapTiles profile
  -> denmark.mbtiles
  -> TileServer GL Light
  -> OSM Liberty rewrite
  -> deterministic mobile style patch
  -> self-hosted style.json + sprites + glyphs
  -> Android app
```

## Runtime components

### Tile backend

- Planetiler generates `infra/data/tiles/denmark.mbtiles` directly from the Denmark OSM extract plus auxiliary OpenMapTiles source data
- TileServer GL Light serves vector tiles from `infra/data/tiles/denmark.mbtiles`
- OSM Liberty-derived style is served from `infra/tileserver/styles/osm-liberty/`
- `infra/scripts/patch-mobile-style.py` applies the M1 mobile cartography pass after the base style rewrite
- Glyph PBFs are served from `infra/tileserver/fonts/` after bootstrap prefetches the exact font stacks used by the style
- The output remains OpenMapTiles-schema-compatible, which preserves a clean path for later style customization and POI interaction work

### Android app

- `MapBackendConfig` owns backend URLs
- `AppContainer` wires the current config plus future service interfaces
- `MapPresentationConfig` owns Denmark-first camera defaults and zoom bounds
- `MapViewModel` owns map screen UI state, lightweight overlay messages, and backend failure state
- `LocationController` isolates MapLibre location enablement, location availability checks, and typed recenter results

## Future extension points

### Search

- App seam: `SearchService`
- Infra seam: `infra/services/nominatim/`
- Planned future base URL: `http://localhost:8081`

### Routing

- App seam: `RoutingService`
- Infra seam: `infra/services/valhalla/`
- Planned future base URL: `http://localhost:8082`

## Why OSM Liberty

OSM Liberty is still the base because it already exposes POIs and feels closer to a consumer map product than a stripped-down developer style. M1 keeps that base but adds a deterministic mobile-specific patch step so Android readability improvements do not turn into hand-edited generated-style drift.
