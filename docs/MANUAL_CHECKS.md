# FocusTag — Manual device checks

These checks cover what the JVM test suite can't reach: real NFC dispatch, the DND/grayscale mode, blocking
from every entry point, reboot and accessibility. Run them on the target device (Pixel 10a, Android 16) for
every build you rely on. `D-xx` refers to `docs/DECISIONS.md`. The list mirrors `docs/PLAN.md` §5.

| Field | Value |
|-------|-------|
| Device | |
| Android build (Settings → About phone → Build number) | |
| App commit (`git rev-parse --short HEAD`) | |
| Date | |
| Tester | |

## Summary

| ID | Check | Result |
|----|-------|--------|
| MC-01 | Pairing writes the tags and doesn't toggle focus | |
| MC-02 | Background scans with the app closed | |
| MC-03 | Unrelated and copied tags are ignored | |
| MC-04 | Blocking from every entry point | |
| MC-05 | Grayscale (mode and fallback) | |
| MC-06 | DND suppression; alarms still ring | |
| MC-07 | Re-assertion after a manual mode switch-off | |
| MC-08 | Reboot in FOCUS | |
| MC-09 | Force-stop in FOCUS | |
| MC-10 | Revoking permissions mid-session; Setup intents | |
| MC-11 | Locked phone ignores scans | |
| MC-12 | Timers across midnight | |
| MC-13 | TalkBack | |
| MC-14 | NFC switched off | |
| MC-15 | Card paired by ID only | |

## Common preconditions

Unless a check says otherwise:

* A fresh debug build is installed (`adb install -r app/build/outputs/apk/debug/app-debug.apk`).
* Every Setup permission is granted (see the README).
* Tag A and Tag B are paired (MC-01).
* At least one app (e.g. YouTube) is on the block list.
* The phone is unlocked.
* The state is FREE TIME.

Write `pass`, `fail` or a short note in each Result cell. For a failure, describe what you saw.

## MC-01 — Pairing writes the tags and doesn't toggle focus

Preconditions: tags not paired yet (fresh install, or reset both in FREE). Use NTAG213/215/216. Have a second
phone or app able to read NDEF (e.g. NFC Tools).

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | Open FocusTag. | It opens on Setup. The Tags card says "Tag scans don't switch focus while this screen is open." | |
| 2 | Tap **Pair** on "Tag A · Desk" and hold a **new, unformatted** tag to the back of the phone. | "Writing… keep the tag still." Then either "Tag A · Desk paired." or "The tag was prepared. Tap it again to finish." | |
| 3 | If asked, tap the same tag again. | "Tag A · Desk paired." The row shows "Paired · …". | |
| 4 | Keep the tag on the phone for 5 s after the dialog says paired, then close the dialog. | No toast. The mode stays FREE TIME, and no "Focus active" notification appears. | |
| 5 | Tap **Pair** on "Tag B · Living room", then hold **Tag A** to the phone. | "This tag is already paired as the other tag. Use another tag." | |
| 6 | Pair Tag B with a second tag. | No "paired" dialog: pairing the second tag completes setup, and the app switches straight to Status (FREE TIME). | |
| 7 | Open Setup again from Status (top-bar settings icon). With Setup open, tap Tag A, then Tag B. | Nothing happens: no toast and no mode change. | |
| 8 | Read Tag A and Tag B with NFC Tools. | Each holds a URI record `focustag://toggle/<uuid>` (two different UUIDs) and an Android Application Record for `io.github.fbarcalar.focustag`. | |
| 9 | Go back from Setup. | The app shows Status, still FREE TIME. | |

## MC-02 — Background scans with the app closed

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | Swipe FocusTag away from Recents. Stay on the home screen. | — | |
| 2 | Tap Tag A. | A toast "Focus on" appears with no visible flicker or delay, and no FocusTag window stays open. The DND mode and grayscale turn on, and the "Focus active" notification appears. | |
| 3 | Tap Tag A again. | Toast "Already in focus". Nothing else changes. | |
| 4 | Tap Tag B. | Toast "Free time". The mode, grayscale and notification turn off. | |
| 5 | Tap Tag B again. | Toast "Already in free time". | |

## MC-03 — Unrelated and copied tags are ignored

Preconditions: a blank NFC tag and an unrelated tag (e.g. a transit card).

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | Tap the unrelated tag. | FocusTag does nothing (another app may react). | |
| 2 | With NFC Tools, copy Tag A's URI (`focustag://toggle/<uuid>`) onto the blank tag. Tap the copy. | No toast and no mode change: the UID doesn't match (D-12). | |
| 3 | In FREE, reset Tag A in Setup and pair a different tag as Tag A. Leave Setup and tap the **old** Tag A. | Nothing happens. The new Tag A works. | |

## MC-04 — Blocking from every entry point

Preconditions: FOCUS (tap Tag A). Before entering FOCUS, open the blocked app once so it is in Recents, and
have a notification from it (or from another blocked app) available.

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | Open the blocked app from the launcher. | The blocking screen "*App* is blocked during Focus" appears at once. | |
| 2 | Tap **Go home**. | The home screen. | |
| 3 | Open the blocked app from Recents. | The blocking screen. | |
| 4 | Tap a notification from the blocked app. | The blocking screen. | |
| 5 | Start split-screen with an allowed app and the blocked app. | The blocking screen covers the blocked app. | |
| 6 | Open the Phone app, the launcher, Settings and FocusTag. | All usable, never blocked. | |
| 7 | Open the blocked app again and press Back on the blocking screen. | It goes to the home screen, not back to the blocked app. | |
| 8 | With the blocked app in the foreground in FREE, tap Tag A. | The blocking screen appears without any new interaction (D-23). | |
| 9 | Tap Tag B while the blocking screen shows. | The blocking screen closes. The app opens normally. | |

## MC-05 — Grayscale (mode and fallback)

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | In FREE, note Settings → Accessibility → Colour correction (on/off, mode). | — | |
| 2 | Tap Tag A. | The screen turns grayscale. | |
| 3 | Tap Tag B. | Colour returns. | |
| 4 | If step 2 didn't turn the screen grey, note it here and continue with the fallback. | — | |
| 5 | `adb shell pm grant io.github.fbarcalar.focustag android.permission.WRITE_SECURE_SETTINGS`. In Setup, turn on **Grayscale fallback**. | The "Grayscale fallback (adb)" row shows as granted. | |
| 6 | Tap Tag A. | Grayscale is on. Colour correction shows as on, in Grayscale mode. | |
| 7 | Tap Tag B. | Colour correction is back **exactly** to the values from step 1 (including "off"). | |
| 8 | Turn colour correction on with another mode (e.g. Deuteranomaly), then repeat steps 6–7. | Step 7 restores that mode. | |
| 9 | Turn the fallback switch off. | — | |

## MC-06 — DND suppression; alarms still ring

Preconditions: a second phone, and a contact that isn't starred.

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | In FOCUS, send yourself a test notification (e.g. a chat message). | No sound or heads-up. It is suppressed. | |
| 2 | Call from the non-starred contact. | The call doesn't ring. | |
| 3 | Set an alarm for one minute later. | The alarm **rings** in FOCUS. | |
| 4 | Tap Tag B, then repeat steps 1–2. | The notification and the call are delivered normally. | |

## MC-07 — Re-assertion after a manual mode switch-off

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | In FOCUS, open Quick Settings and turn off the "FocusTag focus" mode. | Within a few seconds FocusTag re-asserts it (D-34). | |
| 2 | Record the **outcome**: open FocusTag Status. | Either no warning, or "Do Not Disturb and grayscale are not active". Write down which. | |
| 3 | Record the **actual state**: the Quick Settings tile, the DND icon in the status bar, grayscale. | Write down each one: on or off. If step 2 says active but the mode is off, Android kept the user override. | |
| 4 | Repeat steps 1–3 from Settings → Modes → FocusTag focus. | Same records. | |
| 5 | Swipe FocusTag from Recents, then repeat step 1. | Record whether the mode comes back without opening the app (the zen receiver lives only while the process runs). If it doesn't, note it as a gap. | |
| 6 | Tap Tag B. | Everything turns off. | |

## MC-08 — Reboot in FOCUS

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | In FOCUS, note the session timer. Reboot the phone. | — | |
| 2 | Unlock the phone and wait about 30 s. | The mode, grayscale and "Focus active" notification are all on. | |
| 3 | Open a blocked app. | The blocking screen (the accessibility service re-bound). | |
| 4 | Open Status. | FOCUS. The session timer continues from before the reboot (it includes the downtime). | |

## MC-09 — Force-stop in FOCUS

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | In FOCUS: Settings → Apps → FocusTag → Force stop. | — | |
| 2 | Without opening FocusTag, open a blocked app. | Record whether it is blocked. The accessibility service may need a moment to re-bind. | |
| 3 | Tap Tag A. | "Already in focus". The state is consistent, the effects are on, and blocking works again without opening the app. | |
| 4 | Tap Tag B. | FREE. Everything turns off. | |

## MC-10 — Revoking permissions mid-session; Setup intents

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | In FOCUS, revoke DND access in Settings. | No crash. Status shows the banner naming "Do Not Disturb access" and the notice "Do Not Disturb and grayscale are not active". | |
| 2 | Re-grant DND access. | The banner and notice disappear, and the mode is on again. | |
| 3 | Disable the FocusTag accessibility service. | No crash. The banner names "Accessibility service". Blocked apps open (expected, D-46). | |
| 4 | Re-enable it. | The banner disappears, and blocked apps are blocked again. | |
| 5 | In Setup, tap each permission row's button (Open settings / App info / Allow / Copy). | Each opens the matching system screen (NFC, Accessibility, App info, DND access, notification prompt, battery optimisation), or copies the adb command. | |

## MC-11 — Locked phone ignores scans

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | Lock the phone (screen on, lock screen showing). Tap Tag A. | Nothing happens. Android doesn't dispatch NFC to apps on the lock screen. | |
| 2 | Unlock and tap Tag A. | FOCUS. | |

## MC-12 — Timers across midnight

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | Turn off automatic date & time, and set the clock to 23:58. | — | |
| 2 | Tap Tag A. Watch Status across 00:00. | The session timer keeps counting. "Today" resets at midnight and counts only the minutes after 00:00. | |
| 3 | At about 00:03, tap Tag B. | Today ≈ 3 min. The session total isn't double-counted. | |
| 4 | Restore automatic date & time. | — | |

## MC-13 — TalkBack

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | Turn on TalkBack. Open Status in FOCUS. | The mode is read as a heading ("FOCUS"). | |
| 2 | Swipe to the timers. | They are read as words (e.g. "Current session, 1 hour, 5 minutes"), not as "01:05:00". | |
| 3 | Revoke DND access and return to Status. | The warning banner is read as one button. The names line is read as text. | |
| 4 | Tap Tag B while Status is open. | The mode change ("FREE TIME") is announced. | |
| 5 | Turn TalkBack off. Re-grant DND access. | — | |

## MC-14 — NFC switched off

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | Turn NFC off. Open Setup. | The Tags card shows "NFC is off. Turn it on to pair tags." The NFC row says "Required · Missing", and Status shows the banner. | |
| 2 | Tap **Open NFC settings**. | The NFC settings screen opens. | |
| 3 | Turn NFC on and go back. | The prompt disappears, and Pair is available. | |

## MC-15 — Card paired by ID only (D-62)

Use a transport, access or loyalty card you don't mind tapping. Nothing is written to it.

| # | Step | Expected | Result |
|---|------|----------|--------|
| 1 | In Setup (FREE), Re-pair or Pair **Tag B** and tap the card. | "This card can't store FocusTag data, but it can be paired by its ID. Tap the same card again to confirm." | |
| 2 | Tap the same card again. | Paired; Tag B's card reads "Paired by ID only · …". If it says "This card changes its ID on every tap", this card can't be used; stop here. | |
| 3 | Leave Setup, close the app, and tap Tag A, then the card. | FOCUS, then FREE TIME, each with its toast. If nothing happens, reopen Setup: "Open from NFC tags" must be *Granted*, and "Last tap outside Setup" should show the card's ID. Note what it says. | |
| 4 | In FOCUS, tap a different card (e.g. a bank card). | Nothing happens: no toast, still FOCUS. No app chooser appears (if one does, note which app also claims cards). | |
| 5 | Reboot, unlock, tap the card while in FOCUS. | FREE TIME (card scans stay on after a reboot). | |
| 6 | In Setup, re-pair Tag B with an NTAG sticker. Then tap a bank card with the app closed. | Nothing opens: card scans are off again once no card is paired by ID. | |
| 7 | (Optional) Try to pair a locked tag that opens a website when tapped. | Refused as locked; it isn't offered ID-only pairing. | |

