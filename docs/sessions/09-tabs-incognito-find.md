# Session 9 — Tabs, incognito, and find

## Goal
Add reliable multi-tab browsing, private mode, and find-in-page.
## Required deliverables
- Tab switcher/create/close, bounded WebView lifecycle, incognito isolation policy, find controls and matches.
## Likely files/modules
- `tabs/`, `browser/`, activity state and tests.
## Explicit non-goals
- History/bookmark persistence and download management.
## Acceptance criteria
- Tab state remains isolated; closing releases resources; incognito data is not written to app history; find works.
## Verification commands
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```
