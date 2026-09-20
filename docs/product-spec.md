# Product specification

## Product

LightCopy Browser is an Android browser for developers, researchers, and power users who need immediate access to page content and diagnostics. It launches quickly, uses little memory, contains no tracking, works offline where applicable, and keeps all data on-device. Release APK target: under 15 MB.

## Core journeys

1. Enter a URL or query and browse a site normally.
2. Copy original response HTML or clean visible text with one tap.
3. Inspect console output, filter errors, copy/export logs, and clear them.
4. Enter immersive fullscreen while retaining a configurable exit affordance.
5. Inspect page structure, metadata, storage, and network activity.
6. Use normal/incognito tabs and lightweight local browser utilities.

## Functional requirements

### Browser and copy

- Browse normal websites in Android WebView.
- Copy original page source, rendered DOM, visible text, selected element HTML, URL, title, links, image URLs, and Markdown.
- Give immediate, accessible “Copied” feedback.

### Developer tools

- Capture complete JavaScript console logs; filter by level or errors only; copy, export, and clear.
- Inspect a long-pressed/tapped element and show tag, id, class, HTML, and CSS selector.
- Page Info shows title, URL, SSL state, load time, viewport, meta/Open Graph data, favicon, cookies, localStorage, and sessionStorage with copy/clear actions.
- Network Log lists observable requests with URL, method, and status when available. Document WebView interception limitations honestly.
- Per-tab toggles: JavaScript, ad blocking, and Mobile/Desktop/Custom user agent.

### Browser essentials and utilities

- Multiple tabs, incognito, find-in-page, reader mode, dark mode, full-page screenshot, save HTML/TXT.
- QR URL scanner, Android share sheet, download manager, local history, and bookmarks.

### Fullscreen

- Hide app chrome and system bars.
- Always retain a semi-transparent exit control.
- Positions: bottom-middle (default), bottom-left/right, top-left/right/middle, or saved free-drag position.
- Settings: size, opacity, and auto-hide delay.

### Settings

- Appearance: Light/Dark/AMOLED, address bar position, source font size, line numbers, wrapping, syntax theme.
- Console: timestamps, preserve-on-navigation, auto-clear, level filter.
- Browsing: search engine, default normal/incognito mode, keep-screen-on, desktop default, image blocking.
- Copy defaults: preferred copy format.
- Privacy: clear selected data on exit and Do Not Track.
- Advanced: user-agent list editor, CSS/JS injection, copy vibration, settings import/export, download folder.

## Quality attributes

- Offline-first local persistence; no telemetry or remote account.
- Defensive WebView configuration and narrow, origin-aware script execution.
- Fast cold launch and bounded in-memory logs/tabs.
- Accessible semantics, 48dp targets by default, screen-reader labels, and responsive phone/tablet layouts. Explicit user-selected compact settings may reduce tool sizes and fonts to 5dp/sp; defaults remain accessible.
- UI automation must never depend on live internet content; use local fixtures.
