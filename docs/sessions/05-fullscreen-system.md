# Session 5 — Fullscreen system

## Goal
Deliver immersive browsing with an always-available configurable exit control.
## Required deliverables
- System-bar/app-chrome hiding, seven presets, free drag persistence seam, size/opacity/auto-hide controls.
## Likely files/modules
- `fullscreen/`, activity window handling, UI tests.
## Explicit non-goals
- Complete settings catalog.
## Acceptance criteria
- Fullscreen never traps the user; presets and custom position survive recreation; accessibility remains usable.
## Verification commands
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```
