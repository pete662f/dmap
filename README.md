# dmap

A Denmark-first map and navigation app for **Android and iOS**, built on a
self-hosted stack: own vector tiles, own place index, own routing.

Multimodal routing (car, bike, foot) with Rejseplanen for public transport · a
POI layer that beats OSM alone · speed cameras and hazards · and a location
engine that actually tracks a moving car without hopping.

**The plan is the spec:** [`docs/dmap3-master-plan.html`](./docs/dmap3-master-plan.html)
— every component researched, every choice justified, every deadline named. Read
it before making architectural decisions.

## Layout

```
shared/       Kotlin Multiplatform core — domain, use cases, data, location math
androidApp/   Jetpack Compose shell
iosApp/       SwiftUI shell (XcodeGen spec; the .xcodeproj is generated)
infra/        gateways, tile and data pipelines, bootstrap scripts
docs/         the plan, architecture, setup
```

The rule that shapes all of it: **share everything that is a rule, write
natively everything that is a surface.** Business logic, domain models, network
contracts, persistence and the L1–L5 location pipeline live in `shared/`.
Anything touching a screen, a sensor, a permission dialog or an OS lifecycle is
written twice, on purpose.

## Prerequisites

```bash
brew install mise go-task xcodegen duckdb
mise install                 # Java, Node, Python per .mise.toml
```

Plus Xcode (iOS), Android Studio with an **arm64** emulator image, and a Docker
runtime. On a 16 GB machine, OrbStack is noticeably kinder than Docker Desktop
because it returns unused memory to macOS.

## Quick start

```bash
task test          # shared logic on the JVM — the fast loop
task test:ios      # the same fixtures on Kotlin/Native — the parity gate
task android       # install and launch the Android app
task ios           # build the XCFramework, generate the project, open Xcode
task --list        # everything else
```

Backend URLs are **runtime settings** in each app, not build config. Start the
gateways with `task backend:up` and point the app at them from its own settings
screen. This is why a physical device no longer needs a rebuild to change hosts.

## The development loops

Most work should never start a simulator. The setup is built around three loops,
because on 16 GB the whole stack does not fit in memory at once:

1. **Seconds, no device.** Shared logic: domain, use cases, and the entire
   location pipeline. `task test`. Aim to spend most of your hours here.
2. **A minute, one platform.** UI work — emulator *or* simulator, never both
   except at deliberate parity checkpoints.
3. **Physical devices.** Location, navigation, background lifecycle. Rare,
   because the trace replay harness exists so navigation is tuned from fixtures
   rather than from the motorway.

## Four rules

1. **Every backend is an interface with an `Unavailable` implementation**,
   written before the backend exists, declared in `commonMain` so both shells
   get the same seam.
2. **Backend URLs are a runtime setting**, never a compile-time `BuildConfig`
   chain. dmap2 spent ~120 lines of Gradle and a recurring support question on
   this, and none of it ports to Xcode.
3. **The client never infers what the data should state.** Tap → read
   `place_id` → look it up. No geometry-based guessing, ever.
4. **A feature is not done until it is done on both platforms.** Not "Android
   now, iOS in the catch-up sprint" — the catch-up sprint never happens, and a
   half-ported app is how a shared core rots into an Android core with a Swift
   wrapper. The iOS CI lane is a required check for exactly this reason.

## Current state

**P0b — dual-platform foundations.** Both shells launch to a runtime backend
settings screen from the shared core. The frozen `PlaceId` model, the service
contracts with `Unavailable` implementations, and L1 (gate) + L2 (Kalman filter)
of the location pipeline are implemented and tested on both targets. The map
arrives in P1.

The M2 Android app that preceded this is recoverable at the `dmap2-final` tag.

## Licence

Not yet chosen — see the licence-hygiene section of the plan. Note that the app
source and the *derived data* are separate questions: ODbL share-alike attaches
to a redistributed database, not to client code.
