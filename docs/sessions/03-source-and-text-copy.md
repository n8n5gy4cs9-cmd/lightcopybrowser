# Session 3 — One-tap source and text

## Goal
Implement original HTML, rendered DOM, and clean visible-text extraction with clipboard feedback.
## Required deliverables
- Response source capture policy, safe evaluation scripts, viewers, clipboard abstraction, copy feedback.
## Likely files/modules
- `copy/`, `browser/`, UI tests and local HTML fixtures.
## Explicit non-goals
- Advanced element/link/image/Markdown copy and console tools.
## Acceptance criteria
- Source preserves original fetched HTML where available; text is readable and tag-free; rendered DOM reflects mutations.
## Verification commands
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```
