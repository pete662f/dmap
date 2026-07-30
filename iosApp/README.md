# iosApp

SwiftUI shell. Consumes `:shared` as an XCFramework.

## First-time setup

```bash
brew install xcodegen
./gradlew :shared:assembleDmapSharedXCFramework
cd iosApp && xcodegen generate
open iosApp.xcodeproj
```

`iosApp.xcodeproj` is **generated and gitignored**. `project.yml` is the source
of truth — a `pbxproj` is a merge-conflict generator and an unreviewable diff.
Regenerate after adding or moving files.

## Why the framework is wired up by hand

Kotlin 2.4.20-Beta2 and later generate a Swift Package Manager manifest
automatically alongside the XCFramework. The version catalog pins an older,
stable Kotlin instead, so `project.yml` references the built `.xcframework`
directly.

**When you bump Kotlin to a version with the SPM export task, delete that
manual reference** and consume `:shared` as a local Swift package. The manual
wiring exists only so the project does not start life on a beta compiler.

## Two conventions that are not optional

**Native view models, shared use cases.** Do not export shared `StateFlow` into
SwiftUI screens. `BackendSettingsViewModel` is the pattern: an `@Observable`
Swift class that calls suspend functions on shared types. This keeps the exported
Swift API small and idiomatic, and keeps presentation concerns out of
`commonMain`.

**Keep the exported surface small.** Anything not deliberately part of the Swift
API should be `internal` in Kotlin. Review the framework's public surface like an
API, because that is what it is — `doCopy` in `BackendSettingsView.swift` is a
live example of an export that reads badly in Swift and should be replaced with a
purpose-built helper.

## Apple setup still outstanding (P0b)

- Apple Developer Program enrolment, then set `DEVELOPMENT_TEAM` in `project.yml`
- Signing and provisioning for a physical device
- The `location` background mode and its usage strings are already declared in
  `project.yml`; App Review will ask you to justify them at submission (P7)
