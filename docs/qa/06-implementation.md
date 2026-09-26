# QA 06 — Implementation record (Builder)

Input: `05-decisions.md` (D1–D39). I reviewed every NOW item against the code before building.
All 28 NOW items are implemented. Nine were changed from the spec; one sub-part is deferred.
No NOW item was rejected.

## 1. Review

| D# | Decision | Reason / change |
|---|---|---|
| D1 | Accepted, changed | As specced: Allow, LIVE and chimes are always on. Idle max is 10 min with safe mode off, and turning it off needs a confirm sheet. Settings lines now say what is always on and what safe mode adds. |
| D2 | Accepted | The outbox is per owner and is cleared when the session ends, when the 45 s re-entry window runs out, or when a different Remote connects. |
| D3 | Accepted, changed | len/CRC/cap/ack as specced. A header without `len` (an older build) is **refused** rather than read to EOF. Mixed-version pairs therefore don't sync photos; they still save on the Camera. |
| D4 | Accepted | |
| D5 | Accepted, changed | The spec used one `sessionStartedAt` both for the LIVE timer and for the cap, which it shifts forward while busy. That would make the LIVE clock wrong. They are now two fields: `sessionStartedAt` (timer) and `capStartedAt` (cap, shifted while busy and reset by Continue). |
| D6 | Accepted | Zero-tap connect removed. One tap on the known Camera ("Reconnect") still auto-confirms the code **on the Remote**; the Camera still asks for Allow unless it's inside the re-entry window. |
| D7 | Accepted, changed | Also: rejecting an *accepted* handshake now hangs up instead of calling `rejectConnection`. A late `STATUS_OK` for a handshake cancelled, timed out or replaced on this phone is disconnected rather than connected. "Camera declined" is shown only on the Remote; the Camera, where the spec text would have been wrong, shows nothing. |
| D8 | Accepted | |
| D9 | Accepted | |
| D10 | Accepted | |
| D11 | Accepted | |
| D12 | Accepted | |
| D13 | Accepted | Reason codes → LATER, as in the spec. |
| D14 | Accepted, changed | Also: system Back during a shot opens the same "Photo in progress" confirmation, not only the X. |
| D15 | Accepted | |
| D16 | Accepted | |
| D17 | Accepted, changed | Notification text is updated with `NotificationManager.notify` on the same id instead of calling `startForegroundService` again. The spec's version throws from the background on Android 12+. |
| D18 | Accepted, changed | The rejection says "Countdown already running" or "Still saving the last photo", whichever is true. The spec used the saving text for both. |
| D19 | Accepted | |
| D20 | Accepted | |
| D21 | Accepted | Dim only, as specced. |
| D22 | Accepted | |
| D23 | Accepted | |
| D24 | Accepted, changed; part deferred | Semantics are done. The global `minimumInteractiveComponentSize()` on `pressable` is **deferred**: it changes the measured size of every glass control, including the 40 dp toggles, the 32 dp lens segments and the chips, and I can't check the resulting layout without a device. `selectable()` was replaced by `semantics { selected = … }`, so the spring press and single click action stay. |
| D25 | Accepted, changed | `CodeDigits` keeps its fixed tiles and divides the digit size by `fontScale`; no `heightIn` needed. Permissions and the Camera `WaitingCard` scroll. |
| D26 | Accepted | |
| D27 | Accepted | |
| D28 | Accepted | |
| D29–D39 | LATER | Agreed; not touched. |

**Counts:** 28 NOW items. 19 accepted as specced, 9 accepted with changes (D1, D3, D5, D7, D14, D17, D18, D24, D25), 0 rejected. One sub-part is deferred (D24 minimum touch size).

## 2. What was implemented

Paths are relative to `app/src/main/java/app/holdthatpose/`.

### Phase A: protocol and pure logic

- **D3, D5, D8, D13** — `net/Protocol.kt`:
  - `PhotoHeader.len`/`crc` (CRC32), and `MAX_PHOTO_BYTES = 16 MB`.
  - `readPhotoStream` reads exactly `len` bytes with `readFully`, checks the CRC, and never calls `readBytes()`.
  - `Cmd.PhotoAck` (`"pack"`) and `Cmd.Deleted(id, ok)`.
  - `CameraStatus.muted/capLeft/sessionSec`.
  - `TIMER_STEPS` and `clampTimer`, applied when decoding a shutter.
  - A focus that is NaN or missing decodes to `null`; otherwise it is clamped to 0..1.
- **D2, D3** — `session/PhotoOutbox.kt` (new):
  - `PhotoOutbox`: per-owner entries, `due/markSent/ack/newLink/clear`, cap of 20.
  - `ShotRegistry<U>`: `put/takeIfOwned/clear`.
- **D5, D6, D20** — `session/SessionRules.kt` (new):
  - Constants: re-entry 45 s, cap 30 min + 60 s, shot/capture/await/pending timeouts.
  - `capLeft`, `countsAsActivity`, `acceptPhoto`.
  - `linkErrorMessage`, with the Play services / Nearby status codes as numeric constants in `LinkCodes`, so it stays JVM-testable.
- **D1, D5** — `data/Prefs.kt`: `effectiveTimeout(false, 0) == 600`, a max of 600, and `0` removed from `IDLE_CHOICES`.
- **D7, D20, D22** — `net/NearbyLink.kt`:
  - One handshake at a time. The in-session Remote (`preferredPeerId`) may replace an unconfirmed stranger.
  - Every `Pending` times out after 60 s.
  - `rejectedLocally`, which suppresses the false "declined" message and blocks late `STATUS_OK`.
  - `linkErrorMessage` in `fail()`.
  - `generation` and `stopAllIf`.
  - The Camera cancels any incoming STREAM payload.

### Phase B: Camera side

`session/CameraSession.kt` was rewritten around the new pieces:
- **Outbox and ownership (D2, D3):**
  - owner = `sessionRemoteId`;
  - `newLink()` and a flush on every (re)connect;
  - `PhotoAck` → `ack`;
  - everything is cleared when the Remote is forgotten.
- **Trust (D1, D6):**
  - `autoAccept` only for the in-session Remote, whatever the safe-mode setting;
  - a 45 s `reentryJob` after a drop;
  - chimes are always on, with a new `softChime` on re-entry.
- **Activity and cap (D5):**
  - only `countsAsActivity` commands reset idle;
  - an immediate status when a warning is cleared (R23);
  - safe-mode cap with "Still OK?", `continueSession()`, and `capLeft/sessionSec/muted` in the status.
- **Shutter and lens (D18):** a busy shutter is rejected with a reason. `SetLens` is refused while busy or within 1 s, and a status goes back so the Remote reverts. Focus is throttled to one per 100 ms.
- **Capture robustness (D5, D12, D22):**
  - 5 s per shot and 20 s per capture (`withTimeoutOrNull`);
  - a failed gallery save keeps the photo in `filesDir/unsaved/` and sends `ShutterRejected("Couldn't save on the Camera")`;
  - burst files are always deleted;
  - `shot-*.jpg` leftovers are swept on start.
- **Delete (D8):** `takeIfOwned(id, connected Remote)`, and the reply is `Deleted(id, ok)`. "Deleted by Remote" shows on the Camera.
- **Other:**
  - `isRunning`, `retryAdvertising()`, `notices`;
  - `stop()` uses `stopAllIf(gen)` (R20).
- **D9** — `media/AlarmGuard.kt` (new): raises the alarm stream to ≥ 50 % on start, restores it on stop if unchanged, and reports `muted` (volume 0 or DND "total silence"). `media/Beeper.kt` has `softChime()`.
- **D11** — `session/CameraController.kt`: a lifecycle observer clears `bound`/`camera` on `ON_DESTROY`.
- **D17** — `session/SessionService.kt`:
  - `update(context, role, text)` ("LIVE · name" / waiting text);
  - a **Stop** action (`ACTION_STOP`) → `cameraSession.endSession("Stopped on the Camera")`.
  - New string `notif_stop`, and the waiting text is now "Camera is on — waiting for a Remote".

### Phase C: Remote side

`session/RemoteSession.kt`:
- **Photo intake (D3, D4):**
  - `requested/captured/received` with `acceptPhoto`; duplicates are re-acked and unsolicited pushes dropped;
  - JPEG magic and bounds are checked (≤ 8192 px), plus free space;
  - review bitmap via the new `PhotoStore.decodeScaled(…, 1080)`, at most 12 shots, cleared on stop;
  - `PhotoAck` after saving;
  - "Saving photo…" times out after 30 s ("Photo saved on Camera").
- **Frames (D13):** bounds-decoded, and anything over 2048 px is skipped.
- **Reconnect (D6, D19):**
  - the loop retries until the deadline (discover → connect → wait ≤ 8 s → 1.5 s back-off);
  - `Reconnecting(secondsLeft, awaitingAllow)`, where awaiting Allow extends the deadline by 30 s;
  - the countdown is kept through a drop;
  - Cancel during a drop is queued ("Cancel queued") and sent before any queued shutter;
  - `keepAlive()` hides the idle warning at once;
  - `Deleted(ok = false)` → "Camera copy kept".
- **Delete with Undo (D8):** `scheduleDelete(shot, alsoCamera)` and `undoDelete()`, with a 5 s window. A pending delete is still carried out if the user leaves the screen.
- `suppressAutoConnect` and `delete()` were removed. `isRunning` was added.

### Phase D: UI and resources

- **D11** — `ui/AppRoot.kt`: the screen is saved with `rememberSaveable` and a string saver. A running Camera or Remote session wins at start. The pairing auto-connect plumbing is gone. The manifest `configChanges` gains `smallestScreenSize|keyboard|navigation|density|fontScale|locale|layoutDirection`.
- **D10, D15, D18, D22, D26, D8, D24** — `ui/remote/RemoteScreen.kt`:
  - Back closes Review;
  - `FLAG_SECURE` only while the live view shows;
  - full brightness while Live;
  - the lens picker is disabled while counting or awaiting a photo;
  - the volume shutter works only on the live view (Live or Reconnecting);
  - "Tap Allow on the Camera" when `awaitingAllow`;
  - new notices: "Camera is muted", "Continue on the Camera · Ns", "Safe mode off";
  - polite live regions on the notices and the idle warning;
  - a "Deleted · Undo" pill;
  - on/off state for the four toggles.
- **D8, D23** — `ui/remote/ReviewScreen.kt`: trash opens **Delete here** / **Delete on both** / Cancel. A **Share** button appears for `content://` photos (`ACTION_SEND` + chooser + read grant). The "On both phones" caption is removed. The new Share icon is in `ui/icons/PoseIcons.kt`, drawn in the same 1.6-stroke style.
- **D1, D9, D14, D16, D20, D21, D25** — `ui/camera/CameraScreen.kt`:
  - LIVE is shown whenever connected, as "LIVE · name · m:ss", with a **Stop** chip and a "Safe mode off" chip, drawn above every overlay;
  - a "Still OK?" card with **Continue**;
  - "Sound off", and the "Deleted by Remote" / "Couldn't save" notices;
  - the capture blink is drawn above the dim and lock overlays;
  - dimming: 10 s when connected, 60 s otherwise, never while Pending or asking; dimmed brightness is 0.12 in safe mode when connected, else 0.01;
  - Back is swallowed while locked;
  - X or Back during a shot opens "Photo in progress" [Leave] [Stay];
  - `setHideOverlayWindows` on API 31+ and `filterTouchesWhenObscured`;
  - "Try again" when advertising failed;
  - "Cancel" on "Same code?";
  - Bluetooth and Location hints;
  - the `WaitingCard` scrolls.
- **D6, D20** — `ui/pairing/RemotePairingScreen.kt`: the auto-connect effect is removed. The known Camera sorts first and reads "Reconnect". "Try again" appears on error or when nothing is found. The Bluetooth and Location hints come from the new `ui/permissions/Radios.kt` and open the right settings page. The title "Finding\nCamera…" is unchanged.
- **D1, D27** — `ui/settings/SettingsScreen.kt`:
  - the safe-mode-off confirm sheet ("Turn off safe mode?" / "No session limit. Allow, LIVE and chimes stay on." / **Turn off** · Cancel); turning it on is immediate;
  - no "Off" chip;
  - About: Privacy policy (`R.string.privacy_policy_url`, a **placeholder the owner must replace**), Licences (sheet), and "Version x (n)";
  - `selected` semantics on the chips.
- **D24, D25, D26** — `ui/components/Controls.kt`, `Surfaces.kt`, `ui/theme/Color.kt`:
  - `LensPicker(enabled)` with `selected` semantics;
  - `GlassIconButton(toggle)` gives a TalkBack "On"/"Off" state;
  - `CodeDigits` ignores the font scale inside its fixed tiles;
  - `PaperFaint` alpha goes from 0.38 to 0.55.
- **D25** — `ui/permissions/PermissionScreen.kt`: the screen scrolls, and a 24 dp spacer replaces the weight spacer.
- **D28** — `res/xml/data_extraction_rules.xml` (new) excludes `holdthatpose.xml` from cloud backup and device transfer. It is referenced from the manifest.
- **D16** — the manifest adds `HIDE_OVERLAY_WINDOWS`.
- **README** — the guardrails / safe-mode table is rewritten, and the feature rows, edge cases, wire protocol and About are updated.

## 3. Tests and verification

**Unit tests (JVM, `app/src/test`):**
- `ProtocolTest`:
  - every command and field round-trips, including `PhotoAck`, `Deleted(ok=false)` and the new status fields;
  - a photo round-trips with len/crc;
  - truncated, corrupted, oversized (> 16 MB, rejected without reading the body), missing-len and zero-len streams are all refused;
  - timer clamping (7 → 5, -3 → 0, 2e9 → 10);
  - NaN, missing or non-numeric focus decodes to `null`, and a good focus is clamped;
  - older peers get safe defaults.
- `OutboxTest` (new): due-by-owner, ack, resend after `newLink`, the cap drops the oldest, `clear`, and `takeIfOwned` refuses another owner, `null`, unknown ids and a second take.
- `LogicTest`:
  - `safeModeNeverAllowsOff` updated (`effectiveTimeout(false, 0) == 600`, clamped to 600, no 0 in the choices);
  - `capLeft` at the boundaries;
  - the `countsAsActivity` table;
  - `acceptPhoto` cases;
  - `linkErrorMessage` mapping.

**Emulator smoke test (`app/src/androidTest/.../SmokeTest.kt`):**
- Settings now asserts `onAllNodesWithText("Off").assertCountEquals(0)`, both before and after turning safe mode off.
- The switch opens the confirm sheet: prefs stay `safe = true` until **Turn off** is clicked. There is a new screenshot, `03b_settings_confirm_off`.
- Turning safe mode back on is immediate. The final `idle` is `120`.
- All the other asserted texts are unchanged. `CameraSmokeTest` is unchanged: the new Camera UI either appears only when connected, or is an extra notice that doesn't affect the asserted nodes.

**Local pre-CI check.** There is no Android SDK here, so I did this instead:
- Compiled all non-UI sources (`net/`, `session/` except `CameraController`, `media/`, `data/`) with kotlinc 2.0.21. The classpath was Robolectric's `android-all` (API 34) plus hand-written stubs for Play services Nearby, the AndroidX core helpers and CameraX `ImageProxy`. Zero errors and zero warnings.
- Ran all 35 JVM unit tests: they pass.
- Compiled **every UI file** (except `Type.kt`/`Theme.kt`, which use Android font resources) with the Compose compiler plugin against the Compose Multiplatform 1.7.3 desktop jars (same `androidx.compose.*` API as BOM 2024.12.01). The Android-only APIs were stubbed: `LocalContext`, `LocalView`, `AndroidView`, `BackHandler`, `PreviewView`, `asImageBitmap`, lifecycle, `stringResource`. Zero errors.
- Checked `onAllNodesWithText` and `assertCountEquals` in the ui-test jar.
- CI is still the authority for the parts the stubs can't cover: exact AndroidX/Play services signatures, resources and the manifest, lint and R8.

**Needs a device (not verifiable in CI):**
- two-phone re-entry within 45 s and after it (Allow prompt, "Tap Allow on the Camera");
- photo resend after a mid-transfer drop, and the ack;
- Delete on both / Undo;
- the notification Stop action;
- raising and restoring the alarm volume, and the DND "Sound off" detection;
- `setHideOverlayWindows` / tapjacking;
- LIVE readability at 0.12 brightness;
- `FLAG_SECURE` on the Remote;
- the 30-min cap end to end;
- the Bluetooth/Location hints on Android ≤ 11.

## 4. Deferred and follow-ups

- **D24 (part):** a minimum 48 dp touch target for `pressable`. It needs a visual pass: add `minimumInteractiveComponentSize()` where controls are under 48 dp (the 40 dp toggles, lens segments and chips) and compare screenshots.
- **Follow-ups found while building:**
  - Mixed-version pairs (a pre-D3 Camera with a new Remote) can't sync review copies. Consider bumping `SERVICE_ID` so they don't pair at all, or show "Update the app on the other phone".
  - `linkErrorMessage` uses numeric Nearby/Play services codes (`LinkCodes`). Verify `8007` (radio/Bluetooth) and the `8030..8039` missing-permission range against the Play services docs on the first device run; a wrong code only changes the message.
  - `filesDir/unsaved/` photos (a failed gallery save) have no UI yet. A later pass could retry the save on the next start.
  - The owner must replace the `privacy_policy_url` placeholder before release (D27).
- The LATER items D29–D39 and the deferred sub-parts listed in `05-decisions.md` §4 are unchanged.
