# dmap2

Milestone 2 for a Denmark-first native Android map app backed by a self-hosted OpenMapTiles-compatible stack plus self-hosted place search.

This repo contains:

- [`android`](./android) - native Kotlin Android app using MapLibre Native Android
- [`infra`](./infra) - local Docker-based tile backend, style preparation, and bootstrap scripts
- [`docs`](./docs) - setup, architecture, and roadmap documentation

## What M2 includes

- Self-hosted Denmark vector tiles built locally with Planetiler's OpenMapTiles profile
- Self-hosted OSM Liberty-based style, sprites, and glyphs with a deterministic mobile style patch
- Self-hosted Photon search and reverse geocoding backend for Denmark
- Native Android app with a polished map-first screen
- Tasteful POI presentation from the vector tile style only
- Runtime location permission flow without first-launch interruption
- Styled blue dot / puck and a dedicated locate-me control
- Denmark-first camera defaults, zoom bounds, and smoother recentering
- Debounced typed search with readable result rows
- Selected-place marker plus compact place card
- Rendered POI tap selection plus exact-coordinate long-press pins
- Ambient cache configuration from day one
- Clean extension points for future search, place detail, and routing overlays

## What M2 intentionally does not include

- Routing UI or Valhalla integration
- Offline region downloads
- Favorites, accounts, navigation, or turn-by-turn guidance

## Quick start

### 1. Bootstrap Denmark map data, self-hosted style assets, and Photon search data

```bash
./infra/scripts/bootstrap-denmark.sh
```

This is the heaviest step. It runs the pinned multi-arch Planetiler container, generates a Denmark `.mbtiles`, prepares fully self-hosted style assets under `infra/tileserver/`, and downloads the pinned Photon jar plus the official GraphHopper Denmark `1.x` Photon json dump for local import under `infra/data/search/photon/`.

Glyphs are prefetched into the repo during bootstrap so the app does not depend on public font endpoints at runtime. The style pipeline also applies a deterministic mobile tuning pass so the generated style is ready for the Android presentation.

Photon bootstrap now imports the official Denmark `1.x` json dump into a local database instead of unpacking the prebuilt tar database directly. This is slower the first time but has been more reliable with Photon `1.0.1`.

Photon runtime now uses a Docker-managed `photon_data` volume for the live index. The imported host-side artifacts under `infra/data/search/photon/` remain the source dataset, and `./infra/scripts/up-backend.sh` syncs that dataset into the runtime volume automatically before `docker compose up`.

### 2. Start the local backends

```bash
./infra/scripts/up-backend.sh
```

The Photon container now starts with:

- `PHOTON_JAVA_XMS=1g`
- `PHOTON_JAVA_XMX=2g`
- `PHOTON_JAVA_EXTRA_FLAGS=--add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED`

TileServer is intentionally unchanged in this pass because its local latency was already low enough that Photon was the meaningful backend bottleneck.

The local services are served on:

- Map tiles and style: [http://localhost:8080](http://localhost:8080)
- Search and reverse geocoding: [http://localhost:8081](http://localhost:8081)

Optional validation:

```bash
./infra/scripts/verify-backend.sh
```

### 3. Run the Android app

Emulator defaults are already configured:

- `http://10.0.2.2:8080` for the map backend
- `http://10.0.2.2:8081` for the search backend

For a physical device, set your Mac's LAN IP in the repo root `.env`:

```dotenv
DMAP_HOST_IP=192.168.0.195
```

Then rebuild and reinstall the app so the compiled Android `BuildConfig` picks up the new host.

If you prefer Android-only overrides, you can still create `android/local.properties` and point it at your machine’s LAN IP:

```properties
sdk.dir=/Users/your-user/Library/Android/sdk
dmap.backendUrl=http://192.168.1.10:8080
dmap.searchBackendUrl=http://192.168.1.10:8081
```

Then run:

```bash
./infra/scripts/build-apk.sh
```

The build now prints the compiled backend URLs after Gradle finishes. You can also verify them explicitly with:

```bash
./infra/scripts/verify-android-build-config.sh
```

Physical-device checklist:

1. Set `DMAP_HOST_IP` in the repo root `.env`
2. Start the backends with `./infra/scripts/up-backend.sh`
3. Rebuild and reinstall the Android app
4. Ensure the phone and Mac are on the same Wi-Fi or LAN
5. If it still fails, open these URLs on the phone:
   - `http://<mac-ip>:8080/styles/osm-liberty/style.json`
   - `http://<mac-ip>:8081/status`

If the app UI still shows `Backend: http://10.0.2.2:8080`, the installed APK was built with emulator defaults and must be rebuilt after fixing the config.

Or open [`android`](./android) in Android Studio and run the `app` configuration.

A template lives at [`android/local.properties.example`](./android/local.properties.example).

The repo root `.env` is the shared default source for Android builds and backend scripts. The Android build reads:

- `DMAP_HOST_IP` and derives `:8080`, `:8081`, and `:8082`
- or explicit `DMAP_BACKEND_URL`, `DMAP_SEARCH_BACKEND_URL`, and `DMAP_ROUTING_BACKEND_URL`

Build precedence is:

- explicit Gradle/script overrides
- `android/local.properties`
- repo `.env`
- emulator defaults

The backend helper scripts use the same repo `.env` file:

- `./infra/scripts/build-apk.sh`
- `./infra/scripts/verify-backend.sh`
- `./infra/scripts/up-backend.sh`

## Commands

- Bootstrap data and style assets: `./infra/scripts/bootstrap-denmark.sh`
- Reuse cached downloads on repeat bootstrap: `NO_REFRESH=1 ./infra/scripts/bootstrap-denmark.sh`
- Tune Planetiler heap size: `PLANETILER_JAVA_XMX=8g ./infra/scripts/bootstrap-denmark.sh`
- Tune Planetiler threads: `PLANETILER_THREADS=6 ./infra/scripts/bootstrap-denmark.sh`
- Install a Linux-built MBTiles artifact locally: `./infra/scripts/install-mbtiles-artifact.sh /path/to/denmark.mbtiles`
- Start backends: `./infra/scripts/up-backend.sh`
- Verify map + search backends: `./infra/scripts/verify-backend.sh`
- Benchmark map + search backends: `./infra/scripts/benchmark-backend.sh`
- Build Android debug APK: `./infra/scripts/build-apk.sh`
- Build Android release APK: `./infra/scripts/build-apk.sh --release`

## M2 place experience

- The app opens directly into a polished Denmark map instead of immediately jumping to the user
- POIs remain fully style-driven from the vector tile stack
- Location permission is requested from the compact prompt or the locate control, not by interrupting first launch
- The blue dot uses explicit MapLibre location component styling
- Recentering animates to the user only when the user asks for it
- Search requests are debounced and biased to Denmark
- Search results are normalized into readable titles and concise subtitles
- Selecting a search result or tapping a visible rendered POI places a single selected-place marker and opens a compact place card immediately
- Long-press drops a pin at the exact pressed coordinate and only uses a reverse-geocoded label when the returned place is very close to that coordinate
- Empty taps do not clear the current selection and do not snap to nearby POIs

## Backend endpoints

- Style JSON: [http://localhost:8080/styles/osm-liberty/style.json](http://localhost:8080/styles/osm-liberty/style.json)
- TileJSON: [http://localhost:8080/data/openmaptiles.json](http://localhost:8080/data/openmaptiles.json)
- Photon status: [http://localhost:8081/status](http://localhost:8081/status)
- Photon search: [http://localhost:8081/api?q=aarhus&limit=3](http://localhost:8081/api?q=aarhus&limit=3)
- Photon reverse: [http://localhost:8081/reverse?lon=12.5683&lat=55.6761&limit=1](http://localhost:8081/reverse?lon=12.5683&lat=55.6761&limit=1)

## Apple Silicon

Apple Silicon is supported for both map and search in M2.

- Tile generation uses `ghcr.io/onthegomap/planetiler:0.10.2`, which publishes `linux/arm64`
- Tile serving uses `maptiler/tileserver-gl-light:v5.5.0`, which also publishes `linux/arm64`
- Photon runs in a small Java 21 container and uses the official GraphHopper Denmark `1.x` dump
- Photon keeps its imported source data on the host but serves the live OpenSearch index from a Docker-managed volume for better Docker Desktop performance
- The Android app contract is unchanged: it still loads fully self-hosted style, tiles, and search URLs

## Docs

- Setup: [`docs/setup.md`](./docs/setup.md)
- Architecture: [`docs/architecture.md`](./docs/architecture.md)
- Roadmap: [`docs/roadmap.md`](./docs/roadmap.md)
