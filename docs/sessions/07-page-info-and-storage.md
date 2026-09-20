# Session 7 — Page info and storage

## Goal
Build the complete page-information and site-storage panel.
## Required deliverables
- Title/URL/SSL/load/viewport, meta/Open Graph/favicon, cookies, local/session storage, copy and scoped clear.
## Likely files/modules
- `inspect/`, `browser/`, page-info UI and tests.
## Explicit non-goals
- Network request logging and global privacy clearing.
## Acceptance criteria
- Values reflect the active page; storage clear is confirmed and scoped; sensitive data stays on-device.
## Verification commands
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```
