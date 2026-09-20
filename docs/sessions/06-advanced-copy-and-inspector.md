# Session 6 — Advanced copy and inspector

## Goal
Implement advanced copy formats and element inspection.
## Required deliverables
- Copy URL/title/links/images/Markdown/selected HTML; selection overlay; tag/id/class/selector panel.
## Likely files/modules
- `copy/`, `inspect/`, injected scripts, tests.
## Explicit non-goals
- Metadata/storage panel and network log.
## Acceptance criteria
- Long-press inspection selects the intended element; generated selector is stable enough to re-query; all formats copy correctly.
## Verification commands
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```
