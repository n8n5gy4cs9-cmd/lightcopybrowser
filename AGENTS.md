# LightCopy Browser Agent Guide

Read `docs/current-session.json`, then the referenced brief in `docs/sessions/` before editing.

## Session protocol

- Complete only the active session. Do not pull later-session work forward.
- Preserve the requirements in `docs/product-spec.md`; it is the product source of truth.
- Keep the APK lightweight: prefer Android/Compose APIs over large third-party libraries.
- Keep browser data on-device. Do not add analytics, trackers, accounts, or cloud storage.
- Use unidirectional state, small feature packages, and interfaces at Android boundaries.
- Add tests for pure logic and high-risk browser bridges. Never expose broad JavaScript interfaces.
- Run every command listed in the active session brief before advancing the pointer.
- On completion, update `docs/current-session.json` to the next session and add completion evidence.

## UI conventions

- Product UI is a one-handed developer instrument: graphite surfaces, cyan primary actions, restrained corners, compact typography.
- Keep primary actions reachable at the bottom. Source, Text, Console, and Info remain one-tap actions.
- Minimum touch target is 48dp. Important text is at least 14sp and body text is 16sp.
- Every icon-only control needs a content description.

## Commands

Canonical commands live in `README.md`. Do not claim a command passed unless it was run.
