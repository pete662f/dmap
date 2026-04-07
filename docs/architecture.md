# Architecture

## Monorepo

```text
android/  native app
infra/    local tile infrastructure and bootstrap scripts
docs/     setup, architecture, and roadmap
```

## M2 backend

```text
OSM Denmark extract
  -> Planetiler OpenMapTiles profile
  -> denmark.mbtiles
  -> TileServer GL Light
  -> OSM Liberty rewrite
  -> deterministic mobile style patch
  -> self-hosted style.json + sprites + glyphs
  -> Android app

Photon Denmark dump
  -> self-hosted Photon service
  -> forward search + reverse geocoding
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
- `MapPresentationConfig` owns Denmark-first camera defaults, global browsing bounds policy, and zoom limits
- `MapViewModel` owns map screen UI state, lightweight overlay messages, and backend failure state
- `DenmarkCoverage` owns the shared Denmark interaction envelope used to keep global browsing read-only outside local detail/search coverage
- `LocationController` isolates MapLibre location enablement, location availability checks, and typed recenter results
- `SearchService` is now a real service boundary for forward search and reverse geocoding
- `SearchUiState` owns query text, loading/error/empty states, results, and the selected place
- `SelectedPlaceMarkerController` owns the single runtime source/layer used for the current selected place marker

## Future extension points

### Search

- App seam: `SearchService`
- Infra seam: `infra/services/photon/`
- Planned future base URL: `http://localhost:8081`
- Future improvements can add ranking tweaks, recents/history, or a different importer without changing the Android UI layer

### Routing

- App seam: `RoutingService`
- Infra seam: `infra/services/valhalla/`
- Planned future base URL: `http://localhost:8082`

## Why OSM Liberty + Photon

OSM Liberty remains the map base because it already exposes POIs and feels closer to a consumer map product than a stripped-down developer style. The repo keeps that base but adds a deterministic mobile-specific patch step so Android readability improvements do not turn into hand-edited generated-style drift.

The global fallback layer is intentionally lighter. A Natural Earth-derived reference basemap provides land, borders, and major city labels outside Denmark so the map can pan horizontally across the world without committing the repo to a planet-scale OpenMapTiles pipeline.

Photon is the M2 search backend because it gives a practical Denmark-only self-hosted forward-search and reverse-geocoding path with a published Denmark dump. That keeps the milestone small and reliable while preserving a clean future path toward richer ranking or a custom import pipeline.
