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

- Photon service in `infra/services/photon/`
- Real `SearchService` implementation
- Search UI, result markers, and selected-place card
- Reverse geocoding support

## M3

- Valhalla service in `infra/services/valhalla/`
- Real `RoutingService` implementation
- Route origin/destination flow
- Driving, walking, and cycling
- Route line rendering
- Distance and duration summary
- Search-to-route and long-press-to-route flows

## M4

- Maneuver list and richer place actions
- Active navigation session
- Rerouting
- Route alternatives
- Offline regions and offline routing exploration
- Favorites, recents, and saved places
- Data refresh/import hardening
- Production-minded backend topology and observability
