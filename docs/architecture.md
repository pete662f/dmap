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
  -> volume-backed runtime index
  -> forward search + reverse geocoding
  -> Android app

GeoDanmark Ortofoto Web Mercator WMTS
  -> local Ortofoto proxy with Dataforsyningen token
  -> unauthenticated raster tile URL
  -> Android MapLibre raster layer below labels
```

## Runtime components

### Tile backend

- Planetiler generates `infra/data/tiles/denmark.mbtiles` directly from the Denmark OSM extract plus auxiliary OpenMapTiles source data
- TileServer GL Light serves vector tiles from `infra/data/tiles/denmark.mbtiles`
- OSM Liberty-derived style is served from `infra/tileserver/styles/osm-liberty/`
- `infra/scripts/patch-mobile-style.py` applies the M1 mobile cartography pass after the base style rewrite
- Glyph PBFs are served from `infra/tileserver/fonts/` after bootstrap prefetches the exact font stacks used by the style
- The output remains OpenMapTiles-schema-compatible, which preserves a clean path for later style customization and POI interaction work
- TileServer remains on the original bind-mounted layout because it was not the latency bottleneck in this optimization pass

### Search backend

- `infra/scripts/bootstrap-photon.sh` imports the Photon dump into host-managed source artifacts under `infra/data/search/photon/`
- `infra/scripts/sync-photon-volume.sh` copies `photon_data/` plus `.dataset-version` into the Docker-managed `dmap2_photon_data` volume when the dataset changed
- `infra/scripts/up-backend.sh` runs the sync step before `docker compose up`
- Photon serves its live OpenSearch index from the volume-backed `photon_data` path, while `photon.jar` remains bind-mounted from the host
- Photon starts with explicit `Xms`, `Xmx`, and Vector API JVM flags so first-burst search latency is less dependent on heap growth and default module loading

### Ortofoto backend

- `infra/services/ortofoto-proxy/` is a dependency-free Node HTTP service
- The proxy reads `DMAP_ORTHOFOTO_TOKEN_FILE` or `DMAP_ORTHOFOTO_TOKEN`; token files outside the repo are preferred
- Android never receives the Dataforsyningen token; it only calls `/ortofoto/tiles/{z}/{x}/{y}.jpg`
- The proxy forwards valid tile requests to `https://api.dataforsyningen.dk/orto_foraar_webm_DAF`
- Upstream tile requests are timeout-bound and streamed back to clients
- Missing credentials do not block backend startup; tile requests return `503` until the token is configured

### Android app

- `MapBackendConfig` owns backend URLs
- `MapBackendConfig` also derives the Ortofoto tile URL from the imagery backend URL
- `AppContainer` wires the current config plus future service interfaces
- `MapPresentationConfig` owns Denmark-first camera defaults and zoom bounds
- `MapViewModel` owns map screen UI state, lightweight overlay messages, and backend failure state
- `LocationController` isolates MapLibre location enablement, location availability checks, and typed recenter results
- `SearchService` is now a real service boundary for forward search and reverse geocoding
- `SearchUiState` owns query text, loading/error/empty states, results, and the selected place
- `SelectedPlaceMarkerController` owns the single runtime source/layer used for the current selected place marker
- `OrtofotoLayerController` owns the runtime raster source/layer and places imagery under the existing vector labels and POIs

## Future extension points

### Search

- App seam: `SearchService`
- Infra seam: `infra/services/photon/`
- Planned future base URL: `http://localhost:8081`
- Future improvements can add ranking tweaks, recents/history, more caching, or a different importer without changing the Android UI layer

### Routing

- App seam: `RoutingService`
- Infra seam: `infra/services/valhalla/`
- Routing remains stubbed in M2; Valhalla and a runtime routing URL are future work

### Imagery

- App seam: `OrtofotoLayerController`
- Infra seam: `infra/services/ortofoto-proxy/`
- Current base URL: `http://localhost:8083`
- Future improvements can add imagery opacity, historical years, or server-side tile caching without changing the vector tile backend

## Why OSM Liberty + Photon

OSM Liberty remains the map base because it already exposes POIs and feels closer to a consumer map product than a stripped-down developer style. The repo keeps that base but adds a deterministic mobile-specific patch step so Android readability improvements do not turn into hand-edited generated-style drift.

Photon is the M2 search backend because it gives a practical Denmark-only self-hosted forward-search and reverse-geocoding path with a published Denmark dump. That keeps the milestone small and reliable while preserving a clean future path toward richer ranking or a custom import pipeline.
