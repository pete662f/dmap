# Ortofoto Proxy

Small dependency-free proxy for GeoDanmark Ortofoto Foraar Web Mercator tiles.

The service keeps the Dataforsyningen token out of the Android APK and exposes:

- `GET /healthz`
- `GET /ortofoto/tilejson.json`
- `GET /ortofoto/tiles/{z}/{x}/{y}.jpg`

Set `DMAP_ORTHOFOTO_TOKEN` in the repo root `.env` to enable tile forwarding.
