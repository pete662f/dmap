# Photon

Self-hosted search backend for M2.

Responsibilities:

- forward geocoding for typed search
- reverse geocoding for long-press lookup
- Denmark-only local dataset using GraphHopper's Photon Denmark dump

Runtime defaults:

- host URL: `http://localhost:8081`
- in-container port: `2322`
- default language: `da`
- `PHOTON_JAVA_XMS=1g`
- `PHOTON_JAVA_XMX=2g`
- `PHOTON_JAVA_EXTRA_FLAGS=--add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED`

Bootstrap is handled by `infra/scripts/bootstrap-photon.sh`, which downloads a pinned Photon jar plus the Denmark Photon json dump and imports a local database into `infra/data/search/photon/`.

Runtime layout:

- host-managed jar: `infra/data/search/photon/photon.jar`
- host-managed imported source dataset: `infra/data/search/photon/photon_data/`
- host-managed dataset marker: `infra/data/search/photon/.dataset-version`
- Docker-managed runtime volume: `photon_data`

`infra/scripts/up-backend.sh` now runs `infra/scripts/sync-photon-volume.sh` before `docker compose up`. That keeps the live Photon index on Docker-managed storage instead of a macOS bind mount while still treating the host import as the source artifact.

Useful commands:

- start backend: `./infra/scripts/up-backend.sh`
- verify backend: `./infra/scripts/verify-backend.sh`
- benchmark backend: `./infra/scripts/benchmark-backend.sh`
