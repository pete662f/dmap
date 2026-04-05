# dmap2

Milestone 1 for a Denmark-first native Android map app backed by a self-hosted OpenMapTiles-compatible stack.

This repo contains:

- [`android`](./android) - native Kotlin Android app using MapLibre Native Android
- [`infra`](./infra) - local Docker-based tile backend, style preparation, and bootstrap scripts
- [`docs`](./docs) - setup, architecture, and roadmap documentation

## What M1 includes

- Self-hosted Denmark vector tiles built locally with Planetiler's OpenMapTiles profile
- Self-hosted OSM Liberty-based style, sprites, and glyphs with a deterministic mobile style patch
- Native Android app with a polished map-first screen
- Tasteful POI presentation from the vector tile style only
- Runtime location permission flow without first-launch interruption
- Styled blue dot / puck and a dedicated locate-me control
- Denmark-first camera defaults, zoom bounds, and smoother recentering
- Ambient cache configuration from day one
- Clean extension points for future search, place detail, and routing overlays

## What M1 intentionally does not include

- Search UI or Nominatim integration
- Routing UI or Valhalla integration
- Offline region downloads
- Favorites, accounts, navigation, or turn-by-turn guidance

## Quick start

### 1. Bootstrap Denmark map data and self-hosted style assets

```bash
./infra/scripts/bootstrap-denmark.sh
```

This is the heaviest step. It runs the pinned multi-arch Planetiler container, generates a Denmark `.mbtiles`, and prepares fully self-hosted style assets under `infra/tileserver/`.

Glyphs are prefetched into the repo during bootstrap so the app does not depend on public font endpoints at runtime. The style pipeline now also applies a deterministic mobile tuning pass so the generated style is ready for the M1 Android presentation.

### 2. Start the local backend

```bash
docker compose -f infra/compose.yaml up
```

The backend is served on [http://localhost:8080](http://localhost:8080).

Optional validation:

```bash
./infra/scripts/verify-backend.sh
```

### 3. Run the Android app

Emulator default backend URL is already configured to `http://10.0.2.2:8080`.

For a physical device, create `android/local.properties` and point it at your machine’s LAN IP:

```properties
sdk.dir=/Users/your-user/Library/Android/sdk
dmap.backendUrl=http://192.168.1.10:8080
```

Then run:

```bash
cd android
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew assembleDebug
```

Or open [`android`](./android) in Android Studio and run the `app` configuration.

A template lives at [`android/local.properties.example`](./android/local.properties.example).

## Commands

- Bootstrap data and style assets: `./infra/scripts/bootstrap-denmark.sh`
- Reuse cached downloads on repeat bootstrap: `NO_REFRESH=1 ./infra/scripts/bootstrap-denmark.sh`
- Tune Planetiler heap size: `PLANETILER_JAVA_XMX=8g ./infra/scripts/bootstrap-denmark.sh`
- Tune Planetiler threads: `PLANETILER_THREADS=6 ./infra/scripts/bootstrap-denmark.sh`
- Install a Linux-built MBTiles artifact locally: `./infra/scripts/install-mbtiles-artifact.sh /path/to/denmark.mbtiles`
- Start backend: `docker compose -f infra/compose.yaml up`
- Verify backend: `./infra/scripts/verify-backend.sh`
- Build Android debug APK: `cd android && ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew assembleDebug`

## M1 map experience

- The app opens directly into a polished Denmark map instead of immediately jumping to the user
- POIs remain fully style-driven from the vector tile stack
- Location permission is requested from the compact prompt or the locate control, not by interrupting first launch
- The blue dot uses explicit MapLibre location component styling
- Recentering animates to the user only when the user asks for it

## Backend endpoints

- Style JSON: [http://localhost:8080/styles/osm-liberty/style.json](http://localhost:8080/styles/osm-liberty/style.json)
- TileJSON: [http://localhost:8080/data/openmaptiles.json](http://localhost:8080/data/openmaptiles.json)

## Apple Silicon

Apple Silicon is now a supported local path for M1.

- Tile generation uses `ghcr.io/onthegomap/planetiler:0.10.2`, which publishes `linux/arm64`
- Tile serving uses `maptiler/tileserver-gl-light:v5.5.0`, which also publishes `linux/arm64`
- The Android app contract is unchanged: it still loads a fully self-hosted style JSON that points at local MBTiles, sprites, and glyphs

## Docs

- Setup: [`docs/setup.md`](./docs/setup.md)
- Architecture: [`docs/architecture.md`](./docs/architecture.md)
- Roadmap: [`docs/roadmap.md`](./docs/roadmap.md)
