# Session 4 — Console and log export

## Goal
Capture and manage complete per-tab JavaScript console output.
## Required deliverables
- Bounded log model, WebChromeClient capture, level/error filters, timestamps option seam, copy/clear/export, floating entry button.
## Likely files/modules
- `console/`, `browser/`, Compose panels and tests.
## Explicit non-goals
- Network logging and the final settings screen.
## Acceptance criteria
- Log levels/source/line are captured; errors-only is correct; copy/export/clear work without leaking across tabs.
## Verification commands
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```
