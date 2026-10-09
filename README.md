# FocusTag

FocusTag is a personal Android app that turns two NFC tags into a focus switch:

* **Tag A (desk)** starts **FOCUS**: a Do Not Disturb mode with grayscale (Android "Modes"), an ongoing
  "Focus active" notification, and a blocking screen for the apps on your block list.
* **Tag B (living room)** ends it and returns to **FREE TIME**.

There is deliberately **no button in the app that ends focus**: you have to walk to the other tag. The Status
screen shows the mode, the current session and today's total. The Setup screen pairs the tags, lists the
permissions and edits the block list.

It is built for a Pixel 10a on Android 16 and is installed by sideloading (no Play Store).

## Requirements

* A phone with NFC. `minSdk` is 33 (Android 13), but the DND + grayscale mode needs **Android 15 or later**
  (see Known limitations).
* Two writable NFC tags, ideally **NTAG213, NTAG215 or NTAG216**.
* To build: JDK 21, `curl` and `unzip` (the SDK script downloads the Android command-line tools).

## Build

```sh
scripts/setup-android-sdk.sh   # installs the Android SDK pieces and writes local.properties (idempotent)
./gradlew assembleDebug        # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew lint
./gradlew test                 # all automated tests run on the JVM (Robolectric)
```

The script uses `ANDROID_HOME` if it is set, otherwise `/opt/android-sdk` if it exists, otherwise
`~/android-sdk`.

## Install

### From the Releases page (no computer needed)

Every merge to `main` runs `.github/workflows/release.yml`. It runs the tests, builds the APK and publishes it as a GitHub Release named `FocusTag 1.0.<run number>`.

1. On the phone, open the repository's **Releases** page (`https://github.com/FbarcalaR/FocusTag/releases/latest`).
2. Under **Assets**, tap `focustag-1.0.<n>.apk` to download it.
3. Open the download. The first time, Android asks you to allow your browser to **install unknown apps**; allow it and go back.
4. Tap **Install** (or **Update**).

**One-time signing setup (recommended).** Android only installs an update over an existing app if both are signed with the same key. Without a stable key, each release is signed with the build machine's throwaway debug key, and you must uninstall FocusTag before installing a newer release, losing its pairings, block list and permissions. To sign every release with the same key:

1. Create a keystore once (on any computer with a JDK) and keep it, with its passwords, somewhere safe:

   ```sh
   keytool -genkeypair -keystore focustag.jks -alias focustag \
     -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=FocusTag"
   base64 -w0 focustag.jks > focustag.jks.b64   # on macOS: base64 -i focustag.jks
   ```

2. In the repository, open **Settings → Secrets and variables → Actions → New repository secret** and add:

   | Secret | Value |
   |---|---|
   | `FOCUSTAG_KEYSTORE_BASE64` | contents of `focustag.jks.b64` |
   | `FOCUSTAG_KEYSTORE_PASSWORD` | the keystore password |
   | `FOCUSTAG_KEY_ALIAS` | `focustag` |
   | `FOCUSTAG_KEY_PASSWORD` | the key password (the same as the keystore password unless you chose a different one) |

3. Uninstall any FocusTag build signed with a different key once, then install the next release. From then on, releases update in place.

You can also start a release by hand from **Actions → Release → Run workflow**. The keystore is never committed; for a local build signed with it, set the same four names as environment variables, with `FOCUSTAG_KEYSTORE_FILE` pointing at the `.jks` file instead of the base64 secret.

### With adb

1. On the phone, enable **Developer options → USB debugging**, connect it and check that `adb devices` lists it.
2. Install (or update in place):

   ```sh
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

The package name is `io.github.fbarcalar.focustag` (the debug build has no suffix).

## Permissions (in order)

Open FocusTag. It starts on **Setup** until both tags are paired. The **Permissions** card lists every item
with its status ("Required · Missing", "Optional · Granted", …) and a button that opens the right settings
screen. Setup re-checks each time you come back to it. Grant them in this order.

Each step also gives an adb equivalent. Commands marked *(unverified)* are standard Android shell commands
that haven't been tried on the Pixel 10a yet. The on-device path is the reference.

1. **Accessibility service** (required: shows the blocking screen).
   Android marks this switch as a *restricted setting* for sideloaded apps, so first open
   **App info → ⋮ (top right) → Allow restricted settings**, then turn on **FocusTag** under
   **Settings → Accessibility**. The Setup row has both an "Open settings" and an "App info" button.

   ```sh
   adb shell cmd appops set io.github.fbarcalar.focustag ACCESS_RESTRICTED_SETTINGS allow   # (unverified)
   adb shell settings get secure enabled_accessibility_services                              # check first
   adb shell settings put secure enabled_accessibility_services \
     io.github.fbarcalar.focustag/.blocker.FocusAccessibilityService                         # (unverified)
   adb shell settings put secure accessibility_enabled 1                                     # (unverified)
   ```

   The `appops` line only matters for the on-device toggle: it unlocks the greyed-out switch, while the
   `settings put` lines enable the service directly.

   `settings put` **replaces** the whole list. If the `get` printed other services, append ours to that
   value with a `:` separator instead.
2. **Do Not Disturb access** (required: creates and switches the "FocusTag focus" mode, which also turns the
   screen grey). On Android 15+ the "Grayscale" row shows as granted along with it.

   ```sh
   adb shell cmd notification allow_dnd io.github.fbarcalar.focustag   # (unverified)
   ```
3. **Notifications** (required: the "Focus active" notification). Tap "Allow".

   ```sh
   adb shell pm grant io.github.fbarcalar.focustag android.permission.POST_NOTIFICATIONS   # (unverified)
   ```
4. **Battery optimisation** (optional but recommended: keeps re-assertion and boot handling reliable under
   Doze). Allow FocusTag to ignore battery optimisations.

   ```sh
   adb shell dumpsys deviceidle whitelist +io.github.fbarcalar.focustag   # (unverified)
   ```
5. **Grayscale fallback (adb)** (optional). If the mode's grayscale doesn't show on your phone, grant the
   secure-settings permission. The command is also shown in Setup, with a "Copy" button:

   ```sh
   adb shell pm grant io.github.fbarcalar.focustag android.permission.WRITE_SECURE_SETTINGS
   ```

   Then turn on the **Grayscale fallback** switch in Setup. It applies the next time focus starts or the app
   restarts. FocusTag then turns on colour correction (grayscale) during focus and restores your previous
   colour-correction settings afterwards.

**NFC** must also be on (the first row; "Open settings" goes to the NFC settings).

When you're done, every required row shows "Required · Granted". If a required item is later revoked, Status
shows a warning banner that names it. Tap the banner to go to Setup.

## Pairing the tags

1. In Setup → **Tags**, tap **Pair** on "Tag A · Desk" and hold the tag to the back of the phone until it
   says it is paired. Then do the same for "Tag B · Living room".
2. FocusTag writes `focustag://toggle/<random id>` plus an Android Application Record to the tag, and stores
   the id together with the tag's hardware UID.
   * A new, unformatted tag may need two taps: the first prepares it, and the dialog says
     **"The tag was prepared. Tap it again to finish."**
   * A tag can't be paired as both A and B.
   * Locked or too-small tags are refused with a message.
3. Once both tags are paired, the app opens on Status.

Notes:

* **Use NTAG213/215/216.** They have a fixed 7-byte UID. Tags with a random UID can't be validated.
* **Transport, bank, access and loyalty cards won't work.** They are key-protected smart cards (MIFARE DESFire,
  MIFARE Classic, Calypso…) that can't store the FocusTag link, and many use a random UID. The app refuses them
  with "This card can't store FocusTag data" and never tries to format them. Cheap NTAG213/215/216 stickers,
  cards or keyfobs work.
* **While Setup is open, tag scans don't switch focus** (the screen says so). This keeps a pairing tap from
  toggling focus. Leave Setup before scanning.
* A copy of the URI on another tag, an unknown tag, or a tag from an earlier pairing is **ignored**, because
  both the id and the UID must match.
* Reset and Re-pair are locked during focus.

## Daily use

* With the **phone unlocked** (the app can be closed), tap Tag A. A toast says "Focus on". You'll see the
  DND mode, grayscale and the "Focus active" notification. Blocked apps show "*App* is blocked during Focus",
  with a "Go home" button.
* Tap Tag B to end the session ("Free time"). Tapping the same tag twice does nothing ("Already in focus").
* The Status screen shows FOCUS / FREE TIME, the current session (hh:mm:ss) and today's total.
* During focus, Setup still lets you **add** apps to the block list, but not remove them or reset tags.
* Focus survives app kills and reboots: the state is stored on disk and the effects are re-applied when the
  app starts or the phone boots (after the first unlock).

## Manual checks

Some behaviour depends on the real device and can't be tested on the JVM: NFC dispatch, the DND mode and
grayscale, blocking from recents and notifications, and reboot. Before relying on a build:

1. Install a fresh build and note its commit (`git rev-parse --short HEAD`).
2. Pair both tags and grant every permission. These are the preconditions for most checks.
3. Work through [`docs/MANUAL_CHECKS.md`](docs/MANUAL_CHECKS.md) in order. Fill in the Result column and the
   summary table.
4. For each failure, write a short note that points to the related decision (`D-xx` in
   [`docs/DECISIONS.md`](docs/DECISIONS.md)) or check (`MC-xx`).

## Known limitations

* **Escapes outside the app can't be prevented** (D-46). You can still disable the accessibility service,
  revoke DND access or uninstall the app. These are detected and shown in the Status banner, but they aren't
  blocked. Preventing them would need device-owner mode.
* **Switching the mode off from Quick Settings or Settings → Modes:** FocusTag re-asserts it (D-34). Whether
  Android 16 lets an app override the user's manual switch-off is checked on the device (MC-07). If it
  doesn't, Status shows "Do Not Disturb and grayscale are not active".
* **Alarms still ring during focus.** The mode allows alarms and nothing else.
* **Android 13/14 (API < 35):** there's no DND/grayscale mode. Blocking, the tags and the optional grayscale
  fallback still work, and Status shows the effects as not active.
* **The phone must be unlocked** for a scan to work. Android doesn't dispatch NFC to apps on the lock screen.
* Pairing info, the block list and the focus state live in app storage. Uninstalling the app or clearing its
  data loses them, and you have to pair the tags again.
* DataStore 1.2.1 can miss a write that races a new reader. FocusTag reads the first value under the write
  lock to avoid this (D-48).
