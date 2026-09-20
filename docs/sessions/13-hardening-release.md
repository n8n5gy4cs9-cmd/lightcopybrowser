# Session 13 — Hardening and release

## Goal
Make LightCopy release-ready against privacy, accessibility, performance, restoration, and APK-size goals.
## Required deliverables
- Threat/privacy review fixes, accessibility pass, process restoration, performance profiling, R8 rules, release documentation and size gate.
## Likely files/modules
- Entire app, Gradle release config, test matrix, README.
## Explicit non-goals
- Cloud services.

## User-authorized scope addition (2026-09-17)
Implement the browser customization requests tracked in `docs/customization-tasks.md` within this active session. Keep the release hardening requirements and verification gates.
## Acceptance criteria
- Full test suite passes; release APK builds and is under 15 MB; launch/browse/copy/console/fullscreen smoke flow passes on supported API levels.
## Verification commands
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
test $(stat -f%z app/build/outputs/apk/release/app-release-unsigned.apk) -lt 15728640
```
