# Architecture

## Stack

- Kotlin, Android SDK, Jetpack Compose, Material 3.
- Single `app` module initially; packages split by feature before module splitting is justified.
- Minimum API 26, target API 35, compile API 36.

## Shape

```text
MainActivity
  -> BrowserApp (navigation + app-scoped state)
      -> browser/   WebView host, tabs, address actions
      -> copy/      extraction scripts and clipboard/export
      -> console/   WebChromeClient events and bounded log store
      -> inspect/   element selection and page-information scripts
      -> settings/  typed preferences and import/export
      -> data/      Room/local files for history and bookmarks
```

Compose renders immutable UI state and emits typed actions. Android objects (`WebView`, clipboard, downloads, storage) stay behind interfaces owned by the feature that consumes them. A tab owns its WebView state, navigation state, toggles, and ephemeral diagnostics. Incognito state is never persisted.

## Security and privacy

- JavaScript bridges expose the smallest possible surface and are removed when unused.
- Page extraction scripts return serialized values through evaluated callbacks.
- Do not bypass TLS errors. Surface SSL state and let WebView fail safely.
- File/content access, mixed content, third-party cookies, debugging, and downloads use explicit policies.
- Custom injection is visibly opt-in and stored locally.

### Page extraction policy

- Rendered DOM and visible text are read from the current document with fixed, app-owned
  `evaluateJavascript` expressions. Results return only through the evaluation callback;
  no JavaScript interface is installed on the page.
- Original HTML is a credentialed, same-URL HTTP(S) GET made from the page context. This
  preserves response markup rather than serializing the mutated DOM, but it is intentionally
  reported as unavailable for non-HTTP pages, rejected requests, and non-success responses.
  Because it is a fresh GET, dynamic server responses can differ from the navigation response.
- Extraction and clipboard contents remain ephemeral and on-device.

## Size strategy

- Prefer platform WebView and Android APIs.
- Avoid image loaders, networking stacks, and general-purpose dependency bundles unless measured need exists.
- Use vector assets and R8/resource shrinking for release.
- Add an APK-size verification gate before release.
