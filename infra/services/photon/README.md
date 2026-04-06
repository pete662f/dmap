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

Bootstrap is handled by `infra/scripts/bootstrap-photon.sh`, which downloads a pinned Photon jar plus the Denmark Photon json dump and imports a local database into `infra/data/search/photon/`.
