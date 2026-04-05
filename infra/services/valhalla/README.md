# Valhalla Routing

M3 runs a self-hosted Valhalla routing backend on `http://localhost:8082`.

The repo uses the official scripted Docker image:

- `ghcr.io/valhalla/valhalla-scripted:latest`

Local routing input and generated artifacts live under:

- `infra/data/routing/valhalla/`

Expected contents after bootstrap and first startup:

- `denmark.osm.pbf`
- `valhalla.json`
- `denmark_tiles.tar`
- `admins.sqlite`
- `timezones.sqlite`

Bootstrap flow:

1. `bootstrap-denmark.sh` generates the Denmark vector tiles and stages `infra/data/osm/denmark.osm.pbf`
2. `bootstrap-valhalla.sh` copies that extract into `infra/data/routing/valhalla/denmark.osm.pbf`
3. `docker compose -f infra/compose.yaml up` starts Valhalla and lets the official image build its own routing graph

The Android app talks to this backend only through:

- `android/app/src/main/java/com/dmap/services/routing/RoutingService.kt`

Current M3 scope:

- one primary A→B route
- driving, walking, and cycling
- distance and duration summary

Out of scope for this milestone:

- alternatives
- maneuver lists
- rerouting
- active navigation
