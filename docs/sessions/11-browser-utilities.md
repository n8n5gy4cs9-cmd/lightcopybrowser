# Session 11 — Browser utilities

## Goal
Deliver local history/bookmarks, downloads, sharing, and QR URL scanning.
## Required deliverables
- Local data store, history/bookmark UI, DownloadManager integration, share intents, CameraX/ML QR flow with permission rationale.
## Likely files/modules
- `data/`, `history/`, `bookmarks/`, `downloads/`, `qr/` and tests.
## Explicit non-goals
- Accounts, sync, analytics, or general camera capture.
## Acceptance criteria
- Records remain local; incognito leaves no records; downloads/share/QR flows handle cancellation and invalid input.
## Verification commands
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```
