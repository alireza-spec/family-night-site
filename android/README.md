# Family Night — Android app

Native Android wrapper for the Family Night site
(https://alireza-spec.github.io/family-night-site/).

**Why an app at all?** The site's online-play links are direct video files,
and most of them are MKV / H.265 — a format desktop and mobile browsers
cannot decode in a `<video>` element. This app keeps the site exactly as
it is (a WebView loads the live site, so site updates flow into the app
with no app update) and adds one native piece: when the user taps
«پخش با فمیلی نایت», the site hands the direct video URL to a native
ExoPlayer, which plays MKV, H.265/x265 and MP4 in-app.

## How it fits together

- **MainActivity** — a WebView that loads the live site. JavaScript on,
  DOM storage on, file access on (the site's subtitle upload), mixed
  content never allowed (everything is https). Back goes back in WebView
  history, then exits. `intent://` URLs are fired as Android intents, so
  the site's MX Player / VLC buttons keep working. The WebView's
  fullscreen-video callbacks are wired so the site's own player can go
  fullscreen. File chooser is wired for the subtitle upload. Device
  permissions are denied by default.
- **AppBridge** (`window.FamilyNightApp`) — a minimal `@JavascriptInterface`:
  `play(url, title)` and `playWithSubs(url, title, subUrl)`. It accepts
  only plain `https://` URLs with no whitespace and opens PlayerActivity.
  In a normal browser the object does not exist and the site falls back
  to its own in-browser player, unchanged.
- **PlayerActivity** — Media3 ExoPlayer with `StyledPlayerView`.
  `EXTENSION_RENDERER_MODE_ON` lets it fall back to software decoding
  when the device has no hardware H.265 decoder. A browser-like
  User-Agent is set because the video CDN blocks non-browser UAs, and
  the data source follows the hosts' HTTP 302 redirects. Standard
  controls with 10 s forward/back steps, landscape-first fullscreen,
  resume: the position is saved per URL (SHA-256 hash) in
  SharedPreferences and the app offers «ادامه پخش» / «پخش از ابتدا»
  when a saved position is >15 s and < duration−15 s. Subtitles passed
  via `playWithSubs` are added as an SRT/VTT track. On error a Persian
  panel offers retry and copy-link.
- **Site hook** — the site's «پخش با فمیلی نایت» button (v8.1) checks for
  `window.FamilyNightApp` first and calls `play(play_url, title)` when
  present; in browsers the check is a no-op.

## Project

- Single-module Gradle project (Kotlin), `io.github.alirezaspec.familynight`,
  versionCode 1 / versionName 1.0.0, minSdk 24, targetSdk 34, compileSdk 34.
- Gradle 8.5 + Android Gradle Plugin 8.5.2 + Kotlin 1.9.24.
- Media3 (ExoPlayer) 1.3.1.
- Permissions: only `INTERNET` + `ACCESS_NETWORK_STATE`. No ads, no analytics.
- Launcher icon: the site's FN logo (legacy PNGs + adaptive icon on the
  site's dark navy `#0b0e1a`).

## Build locally (Android Studio)

1. Open the `android/` folder in Android Studio (Hedgehog or newer).
2. Let it sync Gradle (JDK 17) and run the `app` configuration.
3. The debug APK installs as `io.github.alirezaspec.familynight.debug`
   alongside the release app.

Command line: `./gradlew :app:assembleDebug` (debug) — the release build
needs the signing keystore, see below.

## Release signing (CI)

Release builds run in GitHub Actions
(`.github/workflows/android-release.yml` at the repo root): manual
`workflow_dispatch` or a push of a `v*` tag. The debug APK is built on
every run as a compile proof; the release APK only when the four signing
secrets are present:

- `KEYSTORE_BASE64` — base64 of the release PKCS12 keystore
- `KEYSTORE_PASSWORD` — keystore password
- `KEY_ALIAS` — key alias (`family-night`)
- `KEY_PASSWORD` — key password (same value as the keystore password)

The workflow decodes the keystore to `app/keystore.p12`, builds
`assembleRelease`, uploads both APKs as Actions artifacts, and on a `v*`
tag also creates a GitHub Release with the signed APK attached. The
keystore file is removed from the runner afterwards and is never
committed (see `.gitignore`).
