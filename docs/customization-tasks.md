# Browser customization tasks — session 13

Requested 2026-09-17. All settings stay on-device and use Android/Compose APIs.

- [x] Address clear X.
- [x] Console settings and five level colors.
- [x] Console + opens live page/console split with X exit and no console toolbar.
- [x] Live placement: bottom/top/left/right; screen proportion 5–95% (35% default).
- [x] Android safe insets, keyboard avoidance, accessible exit.
- [x] Native long-press text selection versus element inspection; visible toggle and hide setting.
- [x] Main action size, placement, icon/label modes; console badge visibility and size.
- [x] Address height and font settings with usable minimums.
- [x] Primary/secondary hex pickers, swatches and ten presets.
- [x] Extra 1: console message search.
- [x] Extra 2: pause/resume console display while capture continues.
- [x] Extra 3: optional console auto-scroll.
- [x] Extra 4: console font sizing.
- [x] Extra 5: reset appearance controls.
- [x] Settings persistence/import/export compatibility and pure logic tests.
- [x] Unit/instrumentation/release verification and APK size gate.

The session pointer stays at 13 until all original release hardening deliverables and acceptance criteria are complete.

## Completion evidence — 2026-09-17

- `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleRelease` passed: 67 JVM tests and 21 instrumentation tests on the existing API 36 ARM64 emulator.
- UI tests verify URL clearing, console-to-settings access, all four non-overlapping live placements, X visibility, and retaining the page composition through live entry/placement/exit.
- Settings tests cover customized round trips, legacy defaults, invalid colors, invalid sizes and pause/capture/resume behavior.
- Final `./gradlew :app:assembleRelease` passed with ARM release packaging.
- `test $(stat -f%z app/build/outputs/apk/release/app-release-unsigned.apk) -lt 15728640` passed: 11,599,062 bytes (11.06 MiB).
- `./gradlew :app:installDebug` passed; normal browser screenshot inspected for bottom gesture clearance.
- Release APK includes arm64-v8a and armeabi-v7a; debug builds retain all emulator architectures. Offline QR scanning remains bundled.
- These new features are complete. Session 13 remains active: its broader original profiling, restoration and supported-API release matrix still require completion. No claim of physical-device or API 26 verification is made.

Final verification also includes keyboard-safe chrome, appearance-aware system-bar icons, and consistent global appearance/selection settings when switching tabs.

## Follow-up fixes — 2026-09-17

- [x] Reproduce empty live console showing only black; show waiting/JavaScript-disabled feedback.
- [x] Verify real local WebView console messages in each live placement, including rendered message pixels.
- [x] Keep live exit on the page side, outside console content.
- [x] Selection toggle: six positions, independent 5–80dp size, avoid shared console-button corner collisions.
- [x] Explicit compact override: 5dp/sp minima for address, actions, source/console fonts and fullscreen exit; defaults retained. Console share 5–95%.
- [x] Optional page status row; address decoration and navigation controls scale instead of enforcing Material field minima.
- [x] Verify follow-up tests and rebuild all three APKs with the existing release signer.

Follow-up evidence: the empty-console regression initially failed because there was no empty-state text; it now passes and newly captured messages replace that status. An offline real WebView fixture emits JavaScript console output, and pixel assertions confirm it renders in all four placements. No capture loss was reproduced. 67 unit tests and 25 device tests passed on API 36. `./build.sh` produced all three APKs, signature/alignment/size checks passed, the release certificate matches the previous signed build, and installation of the signed release on the test emulator succeeded. No temporary debug logging was added.

## Console message button and action alignment follow-up

- [x] Keep show/hide switch; add independent console message button size (5–80dp, default 56dp).
- [x] Independent content: icon + count, icon only, count only; retain accessible message-count description.
- [x] Preserve previous badge size/content when importing older settings.
- [x] Center main action content horizontally and vertically in icons-only and labels-only modes.
- [x] Regression tests, all APK builds and release signature verification.

The alignment regression failed before the fix: Source icon center was 34px above its action center. The action column defaulted to packing content at the top; explicit centered arrangement fixes the placement without adjusting text measurements.

Badge/alignment verification: 67 unit tests and 28 device tests passed on API 36. UI tests check icon/label centers for every main action, independent badge size and visibility, full icon/count text fitting on one line, and legacy settings migration. `./build.sh` rebuilt all three APKs; release signature, alignment and APK size gates passed; certificate matches the previous release.

## Combined view navigation and network quick check

- [x] Back/Forward/Stop/Refresh/Network icon row scoped to live combined view.
- [x] Back, Refresh and Network on by default; Forward/Stop off by default.
- [x] Individual visibility, whole-row visibility, top/bottom placement, 5–80dp size (32dp default).
- [x] Toolbar reserves page space; exit stays available on the opposite edge.
- [x] Network overlay uses console placement/proportion/font/auto-scroll, retains the page and console, and has an X to return to console.
- [x] Stop delegates to WebView.stopLoading; navigation and refresh use the existing browser boundary.
- [x] Tests and rebuilt debug/unsigned/signed release APKs.

Combined-view verification: 68 unit tests and 31 device tests passed on API 36. Tests cover default/individual button visibility, size and top/bottom placement, Stop action, network overlay bounds in every console position, incoming request display, X close, and page retention. `./build.sh` rebuilt all three APKs and verified release signature/alignment/size; signing certificate matches the existing release.
