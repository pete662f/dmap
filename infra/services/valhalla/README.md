# Future Valhalla Slot

M1 does not run a routing backend.

This directory is reserved for M2 so the repo structure does not need to change when routing is added.

Planned responsibility:

- Docker Compose service definition or reverse-proxy wiring for Valhalla
- Tile extraction / routing graph build scripts for Denmark
- API contract docs for app-side `RoutingService`

Expected future local base URL:

- `http://localhost:8082`

Expected app integration seam:

- `android/app/src/main/java/com/dmap/services/routing/RoutingService.kt`
