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
- Reverse geocoding on long-press
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

### 2. Start the local backends

```bash
./infra/scripts/up-backend.sh
```

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

For a physical device, create `android/local.properties` and point it at your machine’s LAN IP:

```properties
sdk.dir=/Users/your-user/Library/Android/sdk
dmap.backendUrl=http://192.168.1.10:8080
dmap.searchBackendUrl=http://192.168.1.10:8081
```

Then run:

```bash
./infra/scripts/build-apk.sh
```

Or open [`android`](./android) in Android Studio and run the `app` configuration.

A template lives at [`android/local.properties.example`](./android/local.properties.example).

You can also set the backend URLs in the repo root `.env`. The Android build now reads:

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
- Selecting a result or long-pressing the map places a single selected-place marker and opens a compact place card

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
- The Android app contract is unchanged: it still loads fully self-hosted style, tiles, and search URLs

## Docs

- Setup: [`docs/setup.md`](./docs/setup.md)
- Architecture: [`docs/architecture.md`](./docs/architecture.md)
- Roadmap: [`docs/roadmap.md`](./docs/roadmap.md)
