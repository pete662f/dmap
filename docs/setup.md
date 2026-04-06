# Setup

## Prerequisites

- Docker Desktop or compatible Docker Engine with `docker compose`
- Android Studio with Android SDK installed
- Java 17+ for Android builds
- `zstd` for Photon dump import during backend bootstrap

Notes for this machine shape:

- The Android SDK is typically under `$HOME/Library/Android/sdk`
- `adb` may not already be on `PATH`; Android Studio can still run the app

## Backend setup

### Bootstrap Denmark map and search data

```bash
./infra/scripts/bootstrap-denmark.sh
```

After the first run, you can reuse the cached source downloads:

```bash
NO_REFRESH=1 ./infra/scripts/bootstrap-denmark.sh
```

What this does:

- runs the pinned `ghcr.io/onthegomap/planetiler:0.10.2` container
- caches source downloads and build intermediates under `infra/.cache/planetiler/`
- builds `infra/data/tiles/denmark.mbtiles`
- copies a Denmark OSM extract into `infra/data/osm/` when available
- rewrites OSM Liberty for fully self-hosted style, sprite, and glyph URLs
- applies a deterministic mobile style patch for the Android presentation
- prefetches the exact glyph PBFs required by the style into `infra/tileserver/fonts/`
- downloads the pinned Photon jar into `infra/data/search/photon/photon.jar`
- downloads the official GraphHopper Denmark `1.x` Photon json dump and imports it into `infra/data/search/photon/photon_data/`

Photon notes:

- the first search bootstrap is slower because the local database is imported from the json dump
- `zstd` must be available on the host for the Photon import step

Recommended tuning knobs:

- `PLANETILER_JAVA_XMX=8g` increases heap for larger local machines
- `PLANETILER_THREADS=6` caps CPU usage if you do not want Planetiler using every logical core
- `FORCE_REBUILD=1` deletes the current Denmark `.mbtiles` and regenerates it

### Apple Silicon notes

Apple Silicon is a supported local path with the current stack:

- Planetiler publishes `linux/arm64` images, so Docker does not need amd64 emulation for tile generation
- `tileserver-gl-light` publishes `linux/arm64` images, so local serving also works natively
- style preparation stays host-side and uses pinned OSM Liberty assets plus prebuilt OpenMapTiles glyph PBFs

You can still generate the MBTiles on another machine and install it here if you want to avoid the heavy local build:

```bash
./infra/scripts/install-mbtiles-artifact.sh /path/to/denmark.mbtiles
```

### Start the backend

```bash
docker compose -f infra/compose.yaml up
```

Useful endpoints:

- `http://localhost:8080/styles/osm-liberty/style.json`
- `http://localhost:8080/data/openmaptiles.json`
- `http://localhost:8081/status`
- `http://localhost:8081/api?q=aarhus&limit=3`

### Verify the backend

```bash
./infra/scripts/verify-backend.sh
```

The verification script now checks both the tile backend and the Photon search backend.

## Android setup

The app uses both `BuildConfig.MAP_BACKEND_URL` and `BuildConfig.SEARCH_BACKEND_URL`.

Default:

- Emulator: `http://10.0.2.2:8080`
- Emulator search: `http://10.0.2.2:8081`

For a physical device, create `android/local.properties`:

```properties
sdk.dir=/Users/your-user/Library/Android/sdk
dmap.backendUrl=http://192.168.1.10:8080
dmap.searchBackendUrl=http://192.168.1.10:8081
```

Build from CLI:

```bash
./infra/scripts/build-apk.sh
```

Optional:

- release build: `./infra/scripts/build-apk.sh --release`
- override backend URLs for the build:
  `./infra/scripts/build-apk.sh --backend-url http://192.168.1.10:8080 --search-backend-url http://192.168.1.10:8081`

Run from Android Studio:

1. Open the `android` directory as a project.
2. Sync Gradle.
3. Run the `app` module on an emulator or device.

## M2 behavior notes

- The app opens on Denmark first and does not auto-jump to the user after permission is granted.
- The locate control and the compact top prompt are the permission entry points.
- The location puck is still MapLibre-native and keeps the M1 styling.
- POIs still come entirely from the self-hosted vector tile style. There is no POI API in M2.
- Search is served by the self-hosted Photon backend, not a public geocoder.
- Search requests are debounced in the app and biased to Denmark using the current map center when available.
- Long-press on the map performs reverse geocoding and falls back to a dropped pin if no useful place label is available.

## Caching

MapLibre ambient cache is enabled in `DmapApplication` with a default limit of `256 MB`.

Already cached in M1:

- vector tiles fetched during normal browsing
- style JSON
- sprite assets
- glyph PBFs

Not yet implemented in M1:

- user-selectable offline regions
- explicit cache eviction UI
- explicit search response caching
- routing response caching

Tune cache size in:

- `android/app/src/main/java/com/dmap/app/DmapApplication.kt`

## Backend tradeoff

The Denmark tile build now uses Planetiler's OpenMapTiles profile instead of the original OpenMapTiles PostGIS quickstart stack. That is the deliberate backend tradeoff for the current milestone stack:

- it is much simpler to run locally, especially on Apple Silicon
- it still outputs OpenMapTiles-compatible vector tiles that fit the OSM Liberty style
- it is a snapshot build pipeline, not an incremental database-backed import pipeline

For glyphs, M1 intentionally downloads the exact prebuilt OpenMapTiles font PBFs needed by OSM Liberty instead of building them locally with `fontnik`. This avoids brittle native toolchain issues on current arm64 Node environments while keeping runtime fully self-hosted.

For style maintenance, the repo intentionally keeps the upstream OSM Liberty snapshot plus a deterministic patch step in `infra/scripts/patch-mobile-style.py`. Future style work should continue through that patch layer instead of manually editing the generated `style.json`.

For search maintenance, M2 intentionally uses Photon plus GraphHopper's official Denmark `1.x` dump as the simplest reliable Denmark-only self-hosted path. A future import pipeline can still be added later without changing the Android search contract because the app already talks only to `SearchService`.
