# Roadmap

## M0

- Self-hosted Denmark vector tile backend
- Self-hosted OSM Liberty-derived style assets
- Native Android map rendering with MapLibre
- Current location permission flow and blue dot
- Ambient caching
- Search and routing service seams only

## M1

- Polished self-hosted Denmark map-first experience
- Deterministic mobile style tuning on top of OSM Liberty
- Better-balanced POI visibility from vector tiles only
- Styled blue dot / puck and a dedicated locate-me control
- Denmark-first camera defaults and smoother recentering
- Lightweight overlay messaging and fallback states

## M2

- Nominatim service in `infra/services/nominatim/`
- Real `SearchService` implementation
- Search UI and result markers
- Reverse geocoding support

## M3

- Valhalla service in `infra/services/valhalla/`
- Real `RoutingService` implementation
- Route line rendering
- ETA, distance, and route alternatives
- Offline region downloads
- Cache tuning and eviction controls
- Rich custom POI interaction
- Data refresh/import hardening
- Production-minded backend topology and observability
