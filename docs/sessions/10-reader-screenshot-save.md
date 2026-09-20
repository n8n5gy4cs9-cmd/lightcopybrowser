# Session 10 — Reader, screenshot, and save

## Goal
Implement focused reading and page capture/export tools.
## Required deliverables
- Reader extraction, page dark transform, full-page screenshot, save original/rendered HTML and text via Storage Access Framework.
## Likely files/modules
- `reader/`, `capture/`, `copy/`, Android document contracts and tests.
## Explicit non-goals
- Download manager and broad file-system permission.
## Acceptance criteria
- Reader degrades gracefully; capture handles tall-page limits; exports use user-selected destinations.
## Verification commands
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```
