# What dmap2 taught

dmap3 reuses **no code** from dmap2. It reuses everything dmap2 learned.

This document exists because those two sentences are in tension: most of the
knowledge below was embodied in scripts and classes that have now been deleted,
and deleting them without writing this down would have thrown away the expensive
part and kept only the empty structure.

Read this before building each corresponding piece. Every item cost real time to
discover.

The code itself is recoverable at the `dmap2-final` tag if you need to look
something up — as a **reference to read**, not a source to copy from. The point
of rebuilding is that each decision gets made deliberately a second time.

---

## The three expensive mistakes

These are the ones worth restructuring the whole app to avoid, and dmap3's
architecture is shaped by them.

### 1. A tapped POI, a search result and a saved place were three unrelated types

There was no stable identifier on POI features in the vector tiles, so tapping a
POI meant *inferring* what had been tapped from rendered geometry. That inference
grew into a **548-line hit detector** plus a parallel 81 MB tileset built only to
support it, and it still got things wrong. Saving a place produced a third
unrelated object that could not be matched back to either the tap or the search
hit.

**What dmap3 does instead:** one `Place` type with one `PlaceId`, emitted onto
the tile features by the tile build itself. Tap → read `place_id` → look it up.
The hit detector does not exist because there is nothing to detect.

**The generalisable rule:** the client never infers what the data should state.
If the client is guessing, the pipeline is missing a field.

### 2. Backend URLs were compile-time configuration

Four URLs resolved through a four-level chain — `.env` → `local.properties` →
Gradle property → default — and baked into `BuildConfig`. That cost roughly 120
lines of Gradle, two helper scripts, a verification script whose entire job was
telling you what got compiled in, and a recurring failure mode: *"the app shows
`10.0.2.2`, so the APK was built with emulator defaults and must be rebuilt."*

Changing which host a physical device talked to required a rebuild and reinstall.

**What dmap3 does instead:** backend configuration is runtime state with a
settings screen. This also happens to be forced by iOS — none of that Gradle
machinery has an Xcode equivalent — but it was the right answer on Android alone.

### 3. The map screen accreted

`MapScreen.kt` reached ~950 lines before anyone tried to decompose it, and the
decomposition was still in progress when dmap2 stopped. Composables, MapLibre
lifecycle handling, runtime side effects and UI state were all in one file
because each addition was individually small.

**What dmap3 does instead:** the split that dmap2 arrived at late is the starting
structure — screen composition, presentation, view lifecycle and runtime effects
as separate units from the first commit. And the `MapController` contract keeps
map-object handling out of feature code entirely.

---

## What worked and should be repeated

**Service interfaces with an `Unavailable` implementation, written before the
backend exists.** This is the single best decision dmap2 made. When the real
search backend arrived it was a drop-in, because the app had only ever talked to
a `SearchService` interface. dmap3 keeps this as rule 1 and extends it to all
eight gateways.

**A deterministic style patch step, never a hand-edited generated file.** The
base style came from upstream at a pinned ref; a script applied every local
change on top. Nobody ever edited the generated `style.json`, so regenerating it
was always safe and upstream changes never had to be re-merged by hand. It had
its own test.

**Ambient tile cache configured on day one** rather than retrofitted.

**Denmark-first camera defaults, and no auto-jump to the user's location** after
a permission grant. The map opens where the product is about Denmark, and moving
the camera stays something the user asked for.

**Verify and benchmark scripts as first-class artifacts.** Being able to answer
"is the backend actually healthy" and "did that change make search slower" from
one command was worth far more than the hour each took to write.

---

## Tile pipeline

- **Planetiler with the OpenMapTiles profile beat the OpenMapTiles PostGIS
  quickstart stack** decisively for local development. Far simpler to run,
  especially on Apple Silicon, and the output stays OpenMapTiles-schema
  compatible so styles remain interchangeable.
- Understand the tradeoff you are accepting: it is a **snapshot build pipeline,
  not an incremental database-backed import**. Fine for weekly rebuilds of one
  country; not fine if you ever want minutely diffs.
- Planetiler and `tileserver-gl-light` both publish `linux/arm64` images, so
  Docker needs no amd64 emulation. Verify this before adopting any new container
  in the pipeline — it is the difference between minutes and hours.
- **Heap is the tuning knob that matters** (dmap2 used 8 GB) and thread count is
  the one that prevents OOM. Make both parameters from the start.
- Cache source downloads and build intermediates separately from outputs, so a
  rebuild does not re-download. dmap2 grew a genuinely useful three-way
  distinction: *refresh upstream sources*, *rebuild local outputs from cache*,
  and *do neither, reuse everything*. Most runs want the third.

### Glyphs — do not build them locally

dmap2 tried building font PBFs with `fontnik` and abandoned it: the native
toolchain is brittle on current arm64 Node. Downloading the exact prebuilt
OpenMapTiles font PBFs the style references is fully self-hosted at runtime and
avoids the entire problem. Prefetch only the stacks the style actually uses.

---

## Search backend

dmap2 used Photon. **dmap3 uses Meilisearch instead**, because Photon was
dmap2's heaviest component by a wide margin. The operational lessons still
transfer:

- **Never put a search engine's live index on a macOS bind mount.** dmap2's
  single biggest backend performance win was moving Photon's OpenSearch index
  into a Docker-managed volume. Bind-mount filesystem overhead on macOS is
  severe for index workloads.
- Keep the distinction between the **imported source artifact** (host-side,
  reproducible, versioned) and the **live runtime index** (volume, disposable).
  Conflating them makes both harder to reason about.
- Write a dataset-version marker so the startup path can detect when a re-sync is
  actually needed. Syncing every start is slow; never syncing is a debugging trap.
- Set JVM `Xms` equal to `Xmx` for anything JVM-based in the pipeline. First-burst
  latency otherwise includes heap growth, which looks like a slow query.
- The host needs `zstd` for compressed dump import. Check tool prerequisites
  explicitly in the bootstrap script rather than failing halfway through.

---

## Imagery proxy

The pattern was right and dmap3 should rebuild it the same shape:

- A **dependency-free** Node HTTP service. It forwards tile requests and nothing
  else; there is no reason for it to have a dependency tree.
- **The client never receives the credential.** It calls a local path and the
  proxy attaches the token server-side. This is the whole point.
- **Prefer a token file outside the repo tree** over an environment variable.
- **Missing credentials must not block startup.** Report healthy, return `503`
  for tile requests. A backend that refuses to start because one optional
  integration is unconfigured is hostile to development.
- Target the provider's **Web Mercator** endpoint so no projection handling is
  needed in the client. Reprojection on-device is a trap.
- Bound the upstream request with a timeout and stream the response back.

---

## Operational

- **Bind to `127.0.0.1` by default**, with an explicit opt-in to a LAN address
  for physical-device testing. Defaulting to `0.0.0.0` is how a development
  backend ends up exposed on café Wi-Fi.
- Physical-device testing has a predictable failure sequence: wrong host, then
  firewall, then wrong port. Being able to test the three URLs from the phone's
  browser resolved most of it, and that should be written into the setup docs
  from the start rather than rediscovered.
- Keep generated data artifacts out of version control and outside the source
  tree. dmap2's grew to ~6.5 GB.

---

## Behaviour details worth carrying forward as decisions

These were tuned by use and are easy to get wrong on a second attempt:

- **Empty map taps must not clear the current selection**, and must not snap to a
  nearby POI. Both feel broken.
- **Long-press drops a pin at the exact pressed coordinate.** Use a
  reverse-geocoded label only when the returned place is genuinely close to that
  coordinate; otherwise the pin claims to be somewhere it isn't.
- **Tapping a visible rendered POI selects it immediately** — no confirmation
  step, no animation gate.
- Search input needs debouncing and should be **biased to the current map
  centre** when one exists.
- When imagery is enabled, **vector labels, POIs, pins and the location puck all
  stay above it.** Imagery goes underneath the information, not on top of it.
