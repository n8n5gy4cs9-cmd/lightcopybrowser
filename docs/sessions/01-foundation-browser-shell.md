# Session 1 — Foundation and browser shell

## Goal

Create a buildable Android project and a polished, responsive Compose shell that establishes LightCopy's visual language and one-handed browser layout.

## Required deliverables

- Gradle Android application configured for Kotlin, Compose, minimum API 26, target API 35, compile API 36, and release shrinking.
- Theme, tokens, icon, activity, and manifest with only baseline internet permission.
- Static but interactive browser shell: status strip, content preview, quick actions, bottom address bar/navigation, and overflow surface.
- Pure state/action model and preview/sample data.
- Unit tests for shell reducer behavior.

## Likely files/modules

- Root Gradle files and `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/dev/lightcopy/browser/`
- `app/src/test/java/dev/lightcopy/browser/`

## Explicit non-goals

- No WebView, network navigation, extraction, clipboard, console capture, tabs, persistence, or settings implementation.
- No permissions beyond internet access.

## Acceptance criteria

- Debug APK compiles.
- Shell renders meaningful LightCopy-specific content with all four one-tap quick actions.
- Address field accepts input and reducer normalizes a submitted URL for future browsing.
- Back/forward/reload and menu affordances are accessible and represented as typed actions.
- Unit tests cover address editing/submission and selected tool state.

## Verification commands

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
