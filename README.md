# FLTR for Android

The companion app for [FLTR-appliance](https://github.com/ascension-association/FLTR-appliance):
see each family member's screen time at a glance, pause, grant extra time, set daily allowances,
and choose which devices belong to whom, all from your phone on your home Wi-Fi.

## What it does

| Tab | What you can do |
|---|---|
| **Family** | One card per person with a ring showing today's time left. Pause/resume and +15 min right on the card. Add people. |
| **Person** (tap a card) | Pause until the daily reset, extra time (+15m … +2h, or take some back), daily allowance (0–24 h in 15-min steps, or FLTR's default), devices (add from what FLTR has seen or by MAC/IP; `rules.txt` devices are shown locked), recent blocks. |
| **Devices** | Everything FLTR has seen on the network, with its software label, last site, and owner. Tap to assign it to a person (by MAC or IP). |
| **Activity** | The block log by day, filterable by person, with why each block happened (always blocked, out of time, paused). |
| **FLTR** | Filtering mode, fail-closed/fail-open, uptime, next reset, rule counts, managed apps, connection details, and *Forget this box*. |

It uses every endpoint of the FLTR app API v1 (`docs/API.md` upstream).

### Pairing and security

* Pair by scanning the QR code from `ramekin logs fltr` (in-app scanner, no Google Play Services
  needed), by pasting the `fltr://pair?…` link, or by scanning with the phone's camera app (the
  app handles `fltr://pair`, and `gkfltr://pair` from older boxes). Links that come from outside the app ask for confirmation first.
* The app checks the pairing by calling `/api/v1/status` before saving it.
* TLS trusts **only** the paired certificate's public key (SHA-256 SPKI pin) and skips host name
  checks, as the API requires. There is no trust-all fallback.
* The URL, token and pin are encrypted with an AES-GCM key held in the Android Keystore. Backups
  are disabled, so the token never leaves the phone.
* Off the home network (or on a 403) the app says "Connect to your home Wi-Fi"; it tells mobile
  data apart from an unreachable box. A 401 leads you to pair again.

### How changes map to the API

`PUT /users/{name}` replaces all of a person's app settings, so the app always sends the
allowance and the full list of app-managed devices together. Adding several devices is one PUT.
For a person who only exists in `rules.txt`, device changes send no allowance, so the
`rules.txt` allowance keeps applying. A device assigned to a new person is removed from the
previous person's app settings first. While the app is open it polls status and users every
30 seconds, as the API recommends.

## Design

* **Artwork:** the official FLTR astronaut by [Catalyst Vibes](https://catalystvibes.com/)
  (`branding/fltr-astronaut.svg`). `tools/svg2vector.py` turns it into the in-app illustration,
  the adaptive launcher icon and the Android 13+ themed (monochrome) icon. After changing the
  SVG, run `python3 tools/svg2vector.py`. Never edit the generated drawables by hand.
* **Palette:** taken from the artwork: plum outlines `#371A45`, lavender shading `#B9AFBE`, sky
  `#33AEF1`, red `#F92323`, with light and dark themes. Cards use the artwork's bold-outline
  "sticker" look.
* **Type:** [Nunito](https://fonts.google.com/specimen/Nunito) (variable font, bundled; SIL OFL,
  see `branding/Nunito-OFL.txt`).
* **UX references:** per-person cards with quick pause and bonus time (Gryphon Connect), people
  who own several devices (Firewalla Users), and a big daily-time ring (the parental-control app on Google Play).

## Building

Requirements: JDK 17+ (JDK 21 recommended) and the Android SDK with platform 37.
[Android Studio](https://developer.android.com/studio) includes both. To use the command line:

```sh
export ANDROID_HOME=$HOME/Android/Sdk
sdkmanager --licenses
sdkmanager "platforms;android-37" "build-tools;36.0.0" "platform-tools"

./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # onto a connected phone or emulator
```

Versions: AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, Gradle 9.8.2, Compose BOM 2026.09.00,
OkHttp 5.5.1, kotlinx.serialization 1.12.0. The app is `minSdk 26`, `compileSdk 37` and
`targetSdk 36`. Raising `targetSdk` to 37 will need Android 17's local-network permission
tested against a real box.

### Trying it without a FLTR box

`tools/mock_fltr.py` serves a fake FLTR API (HTTPS, self-signed, sample family) and prints a
pairing link. Pair the emulator with it:

```sh
python3 tools/mock_fltr.py
adb shell am start -a android.intent.action.VIEW -d "'fltr://pair?…'"   # the printed link
```

## Project layout

```
app/src/main/java/org/ascensionassociation/fltr/
  data/        Pairing link parser, pinned TLS, API client, models, Keystore storage
  ui/          ViewModel, navigation, theme, components, and one package per screen
  util/        Formatting helpers
app/src/test/  Pairing, API (against a real HTTPS server with a self-signed cert), formatting
branding/      Logo SVG and font license
tools/         svg2vector.py, mock_fltr.py
```

## License

Mozilla Public License 2.0, like FLTR itself. The FLTR logo and artwork are not covered by the
MPL. They belong to the FLTR project, from Catalyst Vibes.
