# Session 8 — Network log and developer toggles

## Goal
Add useful WebView request visibility and per-tab browsing controls.
## Required deliverables
- Bounded request list with method/status where observable; JS, ad-block, image, and UA controls; custom UA validation.
## Likely files/modules
- `network/`, `browser/`, filter rules and tests.
## Explicit non-goals
- Claiming DevTools-level response coverage WebView cannot provide; global settings editor.
## Acceptance criteria
- Requests are attributable to the active tab; toggles apply safely after reload; limitations are communicated in UI.
## Verification commands
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```
