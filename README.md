# LightCopy Browser

An ultra-light, privacy-first Android browser for copying page content and inspecting web behavior.

## Requirements

- JDK 17
- Android SDK platforms 35 and 36 and build-tools 35.0.0
- Android emulator or device for launch

## Install dependencies

The Gradle wrapper downloads Gradle and declared Maven dependencies on first use:

```bash
./gradlew help
```

## Develop

Open this directory in Android Studio, or build from the terminal:

```bash
./gradlew :app:assembleDebug
```

## Test

```bash
./gradlew :app:testDebugUnitTest
```

## Build all APKs

```bash
./build.sh
```

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`: signed debug, installable.
- `app/build/outputs/apk/release/app-release-unsigned.apk`: unsigned release for external signing; Android cannot install it.
- `app/build/outputs/apk/release/app-release-signed.apk`: signed release for sideloading.

The script aligns and signs the release, verifies its signature/alignment and checks the 15 MiB size limit. It creates a persistent release key and random password in ignored `.signing/` on first use, then reuses that identity. Back up the entire `.signing/` directory securely: future app updates require the same signing key. Do not commit or share it. Run with JDK 17, Android build-tools 35.0.0 and OpenSSL installed.

For an existing release identity, set `LIGHTCOPY_KEYSTORE`, `LIGHTCOPY_STORE_PASSWORD`, `LIGHTCOPY_KEY_ALIAS` (default `lightcopy`), and optionally `LIGHTCOPY_KEY_PASSWORD` (defaults to the store password). Passwords are passed to signing tools through the environment. `LIGHTCOPY_BUILD_TOOLS_VERSION` can select another installed tools version. SDK location comes from `ANDROID_SDK_ROOT`, `ANDROID_HOME` or `local.properties`.

Debug and release use different signing keys. If the debug app is already installed, Android will reject replacing it with this release; uninstall debug first (this deletes its local browser data), then install signed release. Subsequent signed release builds use the same release key and can update that installation.

`./gradlew :app:assembleRelease` remains available for building only the unsigned release.

## Launch

With an emulator/device connected:

```bash
./gradlew :app:installDebug
adb shell am start -n dev.lightcopy.browser/.MainActivity
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Browser customization

The address field has an X to clear its contents. Long press can use native text selection or element inspection; toggle the Aa / </> button above the main actions, or change it in Settings. The toggle can be hidden.

Settings includes primary/secondary hex colors and swatches, ten color presets, action placement/size and icon/label modes, independent console message button corner/visibility/size (5–80dp), with icon + count/icon/count content choices, address height/font, and reset controls. Defaults retain 48dp controls, a 56dp address field and 14sp fonts. User-selected compact sizes and fonts can go down to 5dp/sp, including the address field, actions, selection toggle and fullscreen exit. Hide the page status row for a smaller complete top bar. The selection toggle has six configurable positions and its own size.

In Console, + enters live debugging: only the page, console and X exit remain. Settings controls console side and screen share (5–95%), five level colors, font and auto-scroll. The normal console also supports search and pause/resume; capture continues while its display is paused, and copy/export uses the displayed search/filter/snapshot. Live mode always shows all current captured messages without timestamps or filter controls. When capture is empty it explains that it is waiting for page console output (or that JavaScript is disabled); the exit X stays on the page side so it cannot cover console messages.

Release APKs package ARM64 and ARMv7 for phones/tablets to meet the 15 MB size gate; debug APKs retain emulator CPU architectures. QR scanning stays on-device with its offline model bundled. Use the signed release output from `./build.sh` for installation.

### Combined-view navigation and network

Settings → Live Debugging Controls configures a top/bottom icon row, its 5–80dp button size (32dp default), whole-row visibility, and visibility of each Back/Forward/Stop/Refresh/Network button. Back, Refresh and Network are enabled by default. Forward/Stop are opt-in; Back/Forward follow history availability and Stop follows loading state.

The row reserves space on the page side. Network overlays the console area using its existing side, screen percentage, font and auto-scroll settings. Its X restores console output while retaining the live page. The network panel shows WebView-observable requests and reported status only; it cannot provide every response status/body. Android Back dismisses the network panel before exiting the combined view.
