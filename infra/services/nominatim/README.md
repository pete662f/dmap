# Future Nominatim Slot

M1 does not run a search backend.

This directory is reserved for M2 so the repo structure does not need to change when search is added.

Planned responsibility:

- Docker Compose service definition or reverse-proxy wiring for Nominatim
- Import/bootstrap scripts for Denmark geocoder data
- API contract docs for app-side `SearchService`

Expected future local base URL:

- `http://localhost:8081`

Expected app integration seam:

- `android/app/src/main/java/com/dmap/services/search/SearchService.kt`
