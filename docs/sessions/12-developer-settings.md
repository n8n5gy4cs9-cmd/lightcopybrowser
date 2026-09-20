# Session 12 — Developer settings

## Goal
Implement the complete settings catalog from the product specification.
## Required deliverables
- Typed local preferences, grouped Material UI, all appearance/console/browsing/copy/privacy/advanced settings, import/export, CSS/JS editors, download folder picker.
## Likely files/modules
- `settings/`, theme, feature policy adapters and tests.
## Explicit non-goals
- Cloud sync or remote configuration.
## Acceptance criteria
- Every specified setting has an effect or explicit platform limitation; import validates schema; clear-on-exit works.
## Verification commands
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```
