# dmap2

Milestone 3 for a Denmark-first native Android map app backed by a self-hosted OpenMapTiles-compatible stack plus self-hosted search and routing.

This repo contains:

- [`android`](./android) - native Kotlin Android app using MapLibre Native Android
- [`infra`](./infra) - local Docker-based tile backend, style preparation, and bootstrap scripts
- [`docs`](./docs) - setup, architecture, and roadmap documentation

## What M3 includes

- Self-hosted Denmark vector tiles built locally with Planetiler's OpenMapTiles profile
- Self-hosted OSM Liberty-based style, sprites, and glyphs with a deterministic mobile style patch
- Self-hosted Photon search and reverse geocoding backend for Denmark
- Self-hosted Valhalla routing backend for Denmark
- Native Android app with a polished map-first screen
- Tasteful POI presentation from the vector tile style only
- Runtime location permission flow without first-launch interruption
- Styled blue dot / puck and a dedicated locate-me control
- Denmark-first camera defaults, zoom bounds, and smoother recentering
- Debounced typed search with readable result rows
- Selected-place marker plus compact place card
- Reverse geocoding on long-press
- Selected-place-first routing flow with current location as the default origin when available
- Driving, walking, and cycling route modes
- Route polyline rendering plus explicit origin and destination markers
- Compact route planner with swap, clear, and route summary
- Ambient cache configuration from day one
- Clean extension points for future navigation, maneuvers, and route alternatives

## What M3 intentionally does not include

- Offline region downloads
- Route alternatives, rerouting, or maneuver lists
- Active navigation, voice guidance, or turn-by-turn guidance
- Favorites or accounts

## Quick start

### 1. Bootstrap Denmark map data, self-hosted style assets, Photon search data, and Valhalla routing input

```bash
./infra/scripts/bootstrap-denmark.sh
```

This is the heaviest step. It runs the pinned multi-arch Planetiler container, generates a Denmark `.mbtiles`, prepares fully self-hosted style assets under `infra/tileserver/`, downloads the pinned Photon jar plus the official GraphHopper Denmark `1.x` Photon database dump under `infra/data/search/photon/`, and stages the Denmark `.osm.pbf` extract into `infra/data/routing/valhalla/` for Valhalla.

Glyphs are prefetched into the repo during bootstrap so the app does not depend on public font endpoints at runtime. The style pipeline also applies a deterministic mobile tuning pass so the generated style is ready for the Android presentation.

### 2. Start the local backends

```bash
docker compose -f infra/compose.yaml up
```

The local services are served on:

- Map tiles and style: [http://localhost:8080](http://localhost:8080)
- Search and reverse geocoding: [http://localhost:8081](http://localhost:8081)
- Routing: [http://localhost:8082](http://localhost:8082)

Optional validation:

```bash
./infra/scripts/verify-backend.sh
```

### 3. Run the Android app

Emulator defaults are already configured:

- `http://10.0.2.2:8080` for the map backend
- `http://10.0.2.2:8081` for the search backend
- `http://10.0.2.2:8082` for the routing backend

For a physical device, create `android/local.properties` and point it at your machine’s LAN IP:

```properties
sdk.dir=/Users/your-user/Library/Android/sdk
dmap.backendUrl=http://192.168.1.10:8080
dmap.searchBackendUrl=http://192.168.1.10:8081
dmap.routingBackendUrl=http://192.168.1.10:8082
```

If you prefer keeping the phone-facing host in a repo-level `.env`, copy [`.env.example`](/Users/peterkaaethorhauge/Projects/dmap2/.env.example) to `.env` and set:

```properties
DMAP_HOST_IP=192.168.1.10
```

The APK rebuild script will derive all three backend URLs from that IP automatically. You can also set explicit overrides in `.env`:

```properties
DMAP_BACKEND_URL=http://192.168.1.10:8080
DMAP_SEARCH_BACKEND_URL=http://192.168.1.10:8081
DMAP_ROUTING_BACKEND_URL=http://192.168.1.10:8082
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
- Prepare Valhalla input only: `./infra/scripts/bootstrap-valhalla.sh`
- Start backends: `docker compose -f infra/compose.yaml up`
- Verify map + search + routing backends: `./infra/scripts/verify-backend.sh`
- Build Android debug APK: `cd android && ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew assembleDebug`
- Rebuild Android debug APK: `./android/scripts/build-debug-apk.sh`

## M3 map and route experience

- The app opens directly into a polished Denmark map instead of immediately jumping to the user
- POIs remain fully style-driven from the vector tile stack
- Location permission is requested from the compact prompt or the locate control, not by interrupting first launch
- The blue dot uses explicit MapLibre location component styling
- Recentering animates to the user only when the user asks for it
- Search requests are debounced and biased to Denmark
- Search results are normalized into readable titles and concise subtitles
- Selecting a result or long-pressing the map places a single selected-place marker and opens a compact place card
- The selected-place card is the routing entry point
- The route planner opens once an endpoint is chosen and defaults the origin to current location when available
- Travel modes map directly to self-hosted Valhalla costing profiles: drive, walk, and bike
- Route loading stays explicit with `Route` / `Update route`, except for the `Route from my location` shortcut
- Loaded routes render as runtime overlays, not baked into the style

## Backend endpoints

- Style JSON: [http://localhost:8080/styles/osm-liberty/style.json](http://localhost:8080/styles/osm-liberty/style.json)
- TileJSON: [http://localhost:8080/data/openmaptiles.json](http://localhost:8080/data/openmaptiles.json)
- Photon status: [http://localhost:8081/status](http://localhost:8081/status)
- Photon search: [http://localhost:8081/api?q=aarhus&limit=3](http://localhost:8081/api?q=aarhus&limit=3)
- Photon reverse: [http://localhost:8081/reverse?lon=12.5683&lat=55.6761&limit=1](http://localhost:8081/reverse?lon=12.5683&lat=55.6761&limit=1)
- Valhalla route: [http://localhost:8082/route](http://localhost:8082/route)

## Apple Silicon

Apple Silicon is supported for the full M3 local stack.

- Tile generation uses `ghcr.io/onthegomap/planetiler:0.10.2`, which publishes `linux/arm64`
- Tile serving uses `maptiler/tileserver-gl-light:v5.5.0`, which also publishes `linux/arm64`
- Photon runs in a small Java 21 container and uses the official GraphHopper Denmark `1.x` dump
- Valhalla uses the official scripted Docker image and builds the Denmark routing graph locally from the staged `.osm.pbf`
- The Android app contract is unchanged: it still loads fully self-hosted style, tiles, and search URLs
- The Android app now also loads routes from the configured self-hosted routing URL

## Docs

- Setup: [`docs/setup.md`](./docs/setup.md)
- Architecture: [`docs/architecture.md`](./docs/architecture.md)
- Roadmap: [`docs/roadmap.md`](./docs/roadmap.md)
