<!-- Squash merge. One PR = one commit on main. -->

## What and why

<!-- One paragraph. The "why" matters more than the "what" — the diff shows the what. -->

Closes #

## Platform parity

<!-- Rule 4: a feature is not done until it is done on both platforms. If this PR
     is deliberately one-sided (a shell-only fix, an infra change), say so and
     why. If it is a feature landing on one platform only, it is not ready. -->

- [ ] Android
- [ ] iOS
- [ ] N/A — explain:

## Checks

- [ ] Shared logic has tests in `commonTest`
- [ ] Trace fixtures updated if the location pipeline changed
- [ ] No new `BuildConfig`-style compile-time configuration
- [ ] Exported Swift surface reviewed if `commonMain` public API changed

## Notes for review

<!-- Anything you want your future self to know in three months. Decisions taken,
     alternatives rejected, things left deliberately undone. -->
