# Session 2 — Secure single-tab browsing

## Goal
Replace the preview surface with a lifecycle-safe WebView that browses websites normally.
## Required deliverables
- WebView host, URL/search normalization, navigation controls, progress, title, error/offline state, and safe defaults.
- Back dispatch and state restoration for one tab.
## Likely files/modules
- `browser/`, `MainActivity.kt`, manifest, browser tests.
## Explicit non-goals
- Copy/extraction, console capture, multiple tabs, persistence.
## Acceptance criteria
- HTTP(S) navigation, search, reload, back, forward, loading and errors work; TLS errors are not bypassed.
## Verification commands
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```
