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
- generates low-resolution world-reference GeoJSON under `infra/tileserver/files/world-reference/`
- applies a deterministic mobile style patch for the Android presentation
- prefetches the exact glyph PBFs required by the style into `infra/tileserver/fonts/`
- downloads the pinned Photon jar into `infra/data/search/photon/photon.jar`
- downloads the official GraphHopper Denmark `1.x` Photon json dump and imports it into `infra/data/search/photon/photon_data/`
- writes `infra/data/search/photon/.dataset-version` so runtime sync can detect when the imported dataset changed

Photon notes:

- the first search bootstrap is slower because the local database is imported from the json dump
- `zstd` must be available on the host for the Photon import step
- the imported host-side Photon data is the source artifact, not the live runtime index

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
./infra/scripts/up-backend.sh
```

Before `docker compose up`, the startup script now syncs the host-side Photon dataset into a Docker-managed `photon_data` volume. That keeps the live OpenSearch index off the macOS bind mount while leaving the jar and imported source artifacts under `infra/data/search/photon/`.

Photon runtime defaults:

- `PHOTON_JAVA_XMS=1g`
- `PHOTON_JAVA_XMX=2g`
- `PHOTON_JAVA_EXTRA_FLAGS=--add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED`

TileServer remains bind-mounted in this pass because it was already performing well enough that the optimization work targeted Photon instead.

Useful endpoints:

- `http://localhost:8080/styles/osm-liberty/style.json`
- `http://localhost:8080/files/world-reference/land.geojson`
- `http://localhost:8080/data/openmaptiles.json`
- `http://localhost:8081/status`
- `http://localhost:8081/api?q=aarhus&limit=3`

### Verify the backend

```bash
./infra/scripts/verify-backend.sh
```

The verification script now checks both the tile backend and the Photon search backend.
It also validates that the served style still contains the world fallback sources and that `/files/world-reference/*` is reachable.
When no explicit URLs are passed, it resolves backend URLs from the repo root `.env`.

### Benchmark the backend

```bash
./infra/scripts/benchmark-backend.sh
```

Default scenarios:

- Photon `/status` warm run
- Photon `/api?q=aarhus&limit=3` warm runs at concurrencies `1`, `4`, and `8`
- Photon reverse-geocode warm run
- tile warm run
- Photon cold first-query run after a container restart

Useful overrides:

- `./infra/scripts/benchmark-backend.sh --mode warm`
- `./infra/scripts/benchmark-backend.sh --mode cold`
- `./infra/scripts/benchmark-backend.sh --requests 100 --concurrency 1,2,4`

## Android setup

The app uses both `BuildConfig.MAP_BACKEND_URL` and `BuildConfig.SEARCH_BACKEND_URL`.

Default:

- Emulator: `http://10.0.2.2:8080`
- Emulator search: `http://10.0.2.2:8081`

For a physical device, set your Mac's LAN IP in the repo root `.env`:

```dotenv
DMAP_HOST_IP=192.168.0.195
```

This is the shared default source for the Android build and the backend helper scripts.

If you want Android-only overrides, you can also create `android/local.properties`:

```properties
sdk.dir=/Users/your-user/Library/Android/sdk
dmap.backendUrl=http://192.168.1.10:8080
dmap.searchBackendUrl=http://192.168.1.10:8081
```

Build from CLI:

```bash
./infra/scripts/build-apk.sh
```

After the build, confirm the compiled backend URLs before installing:

```bash
./infra/scripts/verify-android-build-config.sh
```

Physical-device flow:

1. Set `DMAP_HOST_IP` in the repo root `.env`
2. Start the backends with `./infra/scripts/up-backend.sh`
3. Rebuild and reinstall the Android app
4. Ensure the phone and Mac are on the same Wi-Fi or LAN
5. If the app still cannot connect, test these URLs directly from the phone browser:
   - `http://<mac-ip>:8080/styles/osm-liberty/style.json`
   - `http://<mac-ip>:8081/status`

If the app UI shows `Backend: http://10.0.2.2:8080`, the installed APK was compiled with emulator defaults and needs to be rebuilt after fixing the config.

The Android build can also read backend settings from the repo root `.env`:

- `DMAP_HOST_IP=192.168.1.10` derives backend URLs for ports `8080`, `8081`, and `8082`
- or use explicit `DMAP_BACKEND_URL`, `DMAP_SEARCH_BACKEND_URL`, and `DMAP_ROUTING_BACKEND_URL`

Precedence is:

- explicit script or Gradle `-P` overrides
- `android/local.properties`
- repo `.env`
- emulator defaults

Optional:

- release build: `./infra/scripts/build-apk.sh --release`
- override backend URLs for the build:
  `./infra/scripts/build-apk.sh --backend-url http://192.168.1.10:8080 --search-backend-url http://192.168.1.10:8081`

The shared repo `.env` is used by:

- `./infra/scripts/build-apk.sh`
- `./infra/scripts/verify-backend.sh`
- `./infra/scripts/up-backend.sh`

Run from Android Studio:

1. Open the `android` directory as a project.
2. Sync Gradle.
3. Run the `app` module on an emulator or device.

## M2 behavior notes

- The app opens on Denmark first and does not auto-jump to the user after permission is granted.
- The map can pan globally left-to-right, starts on Denmark, and can now zoom out to a true low-resolution world fallback.
- The locate control and the compact top prompt are the permission entry points.
- The location puck is still MapLibre-native and keeps the M1 styling.
- POIs still come entirely from the self-hosted vector tile style. There is no POI API in M2.
- Search is served by the self-hosted Photon backend, not a public geocoder.
- Search requests are debounced in the app and biased to Denmark using the current map center when available.
- Outside Denmark, the app falls back to a lightweight self-hosted world reference basemap with land, borders, and major city labels served through TileServer's `/files` endpoint.
- Tapping a visible rendered Denmark POI selects that POI immediately.
- Long-press on the map drops a pin at the exact pressed coordinate only inside Denmark and only uses a reverse-geocoded label when the returned place is very close to that coordinate.
- Outside Denmark, browsing stays read-only and the app shows a one-time reminder that place details are Denmark-only for now.
- Empty taps do not clear the current selection and do not snap to nearby POIs.

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

For style maintenance, the repo intentionally keeps the upstream OSM Liberty snapshot plus deterministic patch steps in `infra/scripts/patch-mobile-style.py` and `infra/scripts/patch-world-reference-style.py`. The world reference assets themselves are generated by `infra/scripts/prepare-world-reference.py` under `infra/tileserver/files/world-reference/` and referenced through `file://world-reference/...` URLs so the served style preserves them. Future style work should continue through those patch layers instead of manually editing the generated `style.json`.

For search maintenance, M2 intentionally uses Photon plus GraphHopper's official Denmark `1.x` dump as the simplest reliable Denmark-only self-hosted path. A future import pipeline can still be added later without changing the Android search contract because the app already talks only to `SearchService`.
