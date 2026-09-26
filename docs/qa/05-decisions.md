# QA 05 — Decision record (chaired by Breaker)

**Inputs:**
- Reports: 01 Sentinel (S1–S18), 02 Breaker (R1–R26), 03 Scout (P1–P18).
- Discussions: `04-discussion-{sentinel,breaker,scout}.md`.

**Verification budget:** one engineer, one pass, CI only:
- `./gradlew assembleDebug assembleRelease testDebugUnitTest`
- the emulator smoke tests in `app/src/androidTest` (`SmokeTest`, `CameraSmokeTest`), which **must stay green**.

**Rule applied:** an issue is **NOW** if a majority voted MUST, or if the votes were MUST+SHOULD / SHOULD×3 and the fix is cheap and self-contained. Everything else is **LATER**. Votes are listed as Sentinel/Breaker/Scout.

## 1–2. Consolidated issues and decisions

| ID | Issue | Merges | Votes | Decision |
|---|---|---|---|---|
| D1 | Safe mode off turns the Camera covert: it auto-accepts anyone, hides LIVE when dim, stays silent, and turns off with one tap and no indicator | S3, P1, P2, R26, P13 (Remote shows state) | MUST ×3 | **NOW** |
| D2 | The photo outbox and shot ids leak to the next Remote | R1, S4 | MUST ×3 | **NOW** |
| D3 | Photo stream has no length, cap, CRC or ack: loss, truncation, memory DoS | R2, S6 | len/cap MUST ×3; ack MUST (R) / SHOULD (S) / LATER (P) | **NOW** (ack included, see resolution) |
| D4 | Remote photo intake: OOM from full bitmaps, unsolicited saves, stuck "Saving…" | R3, S5, R10 | MUST ×3 | **NOW** |
| D5 | Idle is easy to bypass: any command counts, `timer` is unbounded, no session cap, a hung capture keeps the session alive forever | S7, P6, R11, S15 (timer) | clamp, intent-only activity, watchdog MUST ×3; cap MUST/SHOULD/SHOULD | **NOW** |
| D6 | Pairing and reconnect trust: zero-tap auto-connect, unbounded silent re-entry, decline loop, reconnect blind to Allow, single attempt | S1, S2, R6, R7 | one-tap and window MUST ×3; retry SHOULD ×3; HMAC MUST/LATER/LATER | **NOW** (HMAC → LATER, D29) |
| D7 | Pending handshake: overwrite and spam, no cancel or timeout, wrong "declined" text | S11, R21, R18 | MUST ×3 (S11); SHOULD+ (R21, R18) | **NOW** |
| D8 | Delete is one tap, hits both phones, can't be undone, and its ack is ignored | P5, S13, R14 | MUST (R) / SHOULD (S) / MUST (P) | **NOW** |
| D9 | "Unmutable" cues play on the alarm stream; the capture blink is drawn under the dim veil | S10, P7 | SHOULD/SHOULD/MUST | **NOW** |
| D10 | System Back in Review disconnects the session | R4 | MUST ×3 | **NOW** |
| D11 | Activity recreation leaves an orphaned session (LIVE hidden, stale `bound`) | R5 (+P8 trigger) | MUST ×3 | **NOW** |
| D12 | A failed gallery save is still reported as "Saved" and the burst files are deleted | R13 | MUST/SHOULD/MUST | **NOW** |
| D13 | Inbound validation: a NaN focus crashes the Camera; frame dimensions are unbounded | S15 (rest) | MUST/MUST/SHOULD | **NOW** (reason codes → LATER) |
| D14 | Leaving the Camera: Back works while locked, a mid-shot exit kills the photo, bystanders have no Stop | R16, P17 | MUST/SHOULD/SHOULD | **NOW** |
| D15 | Silent screen-recording of the live view on the Remote | S8 | MUST/SHOULD/SHOULD | **NOW** (scoped) |
| D16 | LIVE is unreadable at 1 % brightness; overlays and tapjacking | S9 | SHOULD ×3 | **NOW** |
| D17 | Static, misleading notification with no Stop action | S16, P16.5 | SHOULD ×3 | **NOW** |
| D18 | Busy shutter dropped silently; shutter and lens spam; lens switch mid-countdown | R8, S14, R22 | MUST/SHOULD mix | **NOW** |
| D19 | Tapping cancel during a link drop queues a new shot | R9 | SHOULD ×3 | **NOW** |
| D20 | Link start failures are terminal and mislabelled (BT, Location, Play services) | R12, P11 | SHOULD ×3 | **NOW** (minimal) |
| D21 | An unattended, unconnected Camera never dims | R15 | SHOULD ×3 | **NOW** (dim only) |
| D22 | Small reliability fixes: `stopAll` race, idle warning lingers, volume keys, temp files | R20, R23, R24, R25 | SHOULD ×3 | **NOW** |
| D23 | No Share from Review | P4.1 | SHOULD ×3 | **NOW** |
| D24 | TalkBack state and live regions; touch targets under 48 dp | P9, P10 | SHOULD ×3 | **NOW** |
| D25 | Large font clips the pairing code; Permissions and the Allow card don't scroll | P8 (min) | MUST/SHOULD/LATER-ish | **NOW** (min) |
| D26 | Unreadable in bright sun | P12 | SHOULD ×3 | **NOW** |
| D27 | No privacy policy, version or licences in the app | P3 | MUST/SHOULD/MUST | **NOW** |
| D28 | Device-to-device transfer clones trust prefs | S17 | SHOULD ×3 | **NOW** |
| D29 | Cryptographic pairing (session key + HMAC, per-pair keys, rotating adverts) | S1 (full), S2 (gate) | MUST/LATER/LATER | LATER |
| D30 | Broadcast owner name and stable id; editable nickname | S12, P14 | SHOULD/SHOULD/LATER | LATER |
| D31 | Per-frame bitmap allocations | R17 | LATER ×3 | LATER |
| D32 | Half-open link plus idle end shows "Lost" (ended marker in the advert) | R19 | LATER/SHOULD/SHOULD | LATER |
| D33 | Release signed with the debug key | S18 | LATER ×3 | LATER |
| D34 | Full-res fetch, zoom, recent-photos library | P4.2–4 | LATER ×3 | LATER |
| D35 | In-session settings sheet | P13 (rest) | LATER ×3 | LATER |
| D36 | Localisation / RTL | P15 | LATER ×3 | LATER |
| D37 | Play Console tasks (FGS video, audience, Data safety) | P16 | LATER ×3 | LATER |
| D38 | Help / FAQ / feedback | P18 | LATER ×3 | LATER |
| D39 | Disk-persisted outbox and persisted id→uri map | R1 (persist), R14 (map) | LATER ×3 | LATER |

**Totals:** 39 issues, 28 NOW (D1–D28) and 11 LATER (D29–D39). Some NOW items also carry deferred sub-parts, listed in §4.

### Resolved disagreements
- **HMAC session check vs one-tap reconnect (D6/D29).** NOW:
  - Remove every *zero-tap* connect outside a live session. The pairing screen shows a one-tap "Reconnect" row instead.
  - Silent auto-accept is kept only for the in-session Remote, only inside a bounded re-entry window, and always with a chime.

  The HMAC handshake is LATER. It is sound, but a bug in it breaks every reconnect, and this pass has no two-device test. Meanwhile the window, the chime and the always-on LIVE badge bound the spoof risk.
- **Reconnect window.** The Camera window is **45 s** (proposals were 30 / 60 / 30). The Remote retries for 30 s from *its own* detection, and the two phones notice a drop up to ~15 s apart. A Camera-side 30 s window would reject honest reconnects; 45 s stays short and bounded.
- **Session cap (D5).** Safe mode only; not in reach of the Remote.
  - At **30 min** of session time, the Camera chimes and shows "Still OK?" with a **Continue** button.
  - The Remote shows "Continue on the Camera · Ns".
  - With no tap within **60 s**, the session ends.
  - The countdown never ends a session mid-countdown or mid-capture: it waits until idle.
  - The decision is a pure function, so it can be unit-tested.
- **FLAG_SECURE scope (D15).** Set only while the Remote's **live view** is showing. Review stays screenshot-able, so a user can share their own photo. This is friction, not a guarantee.
- **Alarm volume (D9).** At Camera start, if the alarm stream is below 50 %, raise it to 50 % inside `runCatching` and restore it on stop. If that fails, or Do Not Disturb silences alarms, show "Sound off" on the Camera and "Camera is muted" on the Remote. Honest users are never blocked, and bystanders normally hear the cues.
- **Delete default (D8).**
  - Trash opens a choice: **Delete here** (primary) or **Delete on both** (secondary), then a 5 s **Undo**.
  - The Camera honours a delete only for ids owned by the connected Remote in the current session, and replies `Deleted(id, ok)`.
  - A Camera-side opt-in setting is LATER.
- **Photo acceptance on the Remote (D4).**
  - Strict "id must match a received `Captured`" would reject photos synced after a drop at zero, so it is not used. The Remote counts the shutters it sent: it accepts a photo when the id is new and either matches a `Captured` or `received < requested`.
  - It also validates length, CRC and JPEG header, and bounds the dimensions.
  - Unsolicited pushes are refused.
- **Safe mode off (D1).**
  - Bystander guardrails no longer depend on it: Allow for any new Remote, LIVE always visible, chimes always on, and an idle maximum of 10 min.
  - Safe mode off now only removes the 30-min session cap and the LIVE brightness floor.
  - Turning it off needs a confirmation sheet (no biometric; that is LATER).
- **Photo ack (D3).** It is NOW despite the split vote. The outbox class is being rewritten anyway (D2), so the ack is about 30 lines plus a unit test, and it is what makes "photo syncs on reconnect" actually true.

## 3. Implementation spec (NOW), in order

Paths are relative to `app/src/main/java/app/holdthatpose/`. User-facing strings are given in quotes. The product style is minimal text, so don't embellish.

**Cut line:** Phases A–D (D1–D14, D18, D19) are the core. Phase E items (D15–D17, D20–D28) are cheap. If time runs out, cut from the end of Phase E and record what moved to LATER.

### Phase A: protocol and pure logic (with unit tests)

**A1. `net/Protocol.kt` (D3, D5, D13, D8).**
- `PhotoHeader`:
  - Add `len: Int` and `crc: Long` (JSON `"len"`, `"crc"`; `fromJson` uses `optInt("len", -1)` / `optLong("crc", -1)`).
  - `photoStreamBytes` fills both (`java.util.zip.CRC32` over the JPEG).
- Add `const val MAX_PHOTO_BYTES = 16 * 1024 * 1024`.
- `readPhotoStream`:
  - Read the header.
  - `require(header.len in 1..MAX_PHOTO_BYTES)`.
  - `readFully(ByteArray(header.len))`.
  - `require(CRC32 == header.crc)`.
  - Never use `readBytes()`.
- New command `Cmd.PhotoAck(id)` (wire `"pack"`, Remote→Camera).
- Change `Cmd.Deleted(id, ok: Boolean = true)` (`"ok"`, default true).
- `CameraStatus` gains:
  - `muted: Boolean = false` (`"muted"`);
  - `capLeft: Int = -1` (`"capLeft"`, seconds left in the Continue grace; -1 = none);
  - `sessionSec: Int = 0` (`"sess"`, elapsed session seconds, for the LIVE timer).
- Add `val TIMER_STEPS = listOf(0, 3, 5, 10)` and `fun clampTimer(t: Int) = TIMER_STEPS.lastOrNull { it <= t } ?: 0`. `fromJson("shutter")` applies it.
- `fromJson("focus")` returns `null` when x or y is missing or non-finite, and otherwise clamps both to 0..1.

**Tests (`ProtocolTest`):**
- `everyCommandSurvivesTheWire` covers the new commands and fields.
- Photo round trip with len/crc.
- A truncated stream throws.
- A CRC mismatch throws.
- `len > MAX` throws without allocating.
- A timer of 7 becomes 5; -3 and 2_000_000_000 both become 10 or 0 as defined.
- A NaN or missing focus decodes to null.

**A2. New `session/PhotoOutbox.kt` (D2, D3).** Pure Kotlin, no Android types in the logic.
- Entry: `(owner: String, header: PhotoHeader, bytes: ByteArray)`.
- API:
  - `add(owner, header, bytes)` keeps at most `MAX_OUTBOX = 20` entries and drops the oldest.
  - `due(owner): List<Entry>` returns entries for `owner` not yet sent on this link.
  - `markSent(id)`.
  - `ack(id)` removes the entry.
  - `newLink()` clears the sent-marks.
  - `clear()`.

A companion `ShotRegistry` holds `id → (uri: Uri?, owner)` with `put`, `takeIfOwned(id, owner)` and `clear()`. Use `Any` or a generic type for the uri to keep it JVM-testable.

**Tests (`OutboxTest`):**
- `due` returns only the owner's entries.
- `ack` removes.
- An entry stays after `newLink()` until acked.
- The cap drops the oldest.
- `takeIfOwned` refuses another owner.

**A3. New `session/SessionRules.kt` (D5, D6).** Pure functions:
- `const val REENTRY_WINDOW_MS = 45_000L`, `SESSION_CAP_MS = 30 * 60_000L`, `CAP_GRACE_MS = 60_000L`, `SHOT_TIMEOUT_MS = 5_000L`, `CAPTURE_TIMEOUT_MS = 20_000L`, `AWAIT_PHOTO_TIMEOUT_MS = 30_000L`, `PENDING_TIMEOUT_MS = 60_000L`.
- `fun capLeft(elapsedMs: Long, capMs: Long, graceMs: Long): Int` returns -1 before the cap, otherwise the grace seconds left (≥ 0).
- `fun countsAsActivity(cmd: Cmd): Boolean` is true only for `Shutter`, `CancelCountdown`, `SetLens`, `Focus`, `KeepAlive`, `Delete`.
- `fun acceptPhoto(id: String, received: Set<String>, captured: Set<String>, requested: Int): Boolean` is `id !in received && (id in captured || received.size < requested)`.
- `fun linkErrorMessage(action: String, statusCode: Int?): String`:
  - `API_UNAVAILABLE`, `SERVICE_MISSING`, `SERVICE_VERSION_UPDATE_REQUIRED` (`CommonStatusCodes` / `ConnectionResult`) → "Needs Google Play services".
  - `ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR` or `STATUS_RADIO_ERROR` → "Turn on Bluetooth and Wi-Fi".
  - Missing-permission codes → "Permission needed".
  - Otherwise → `action`.
- `Prefs.effectiveTimeout(safeMode, configured)` becomes `if (configured <= 0) (if (safeMode) 60 else 600) else configured`. Remove `0` from `IDLE_CHOICES`.

**Tests (`LogicTest`):**
- Update `safeModeNeverAllowsOff`: `effectiveTimeout(false, 0) == 600`.
- `capLeft` at the boundaries.
- The `countsAsActivity` table (Ping, Hello, Status → false).
- `acceptPhoto` cases: drop-at-zero accepted, unsolicited rejected, duplicate rejected.
- `linkErrorMessage` mapping.

**A4. `net/NearbyLink.kt` (D7, D20, D22).**
- **Reject overlapping requests.** In `onConnectionInitiated`, if the current state is `Pending` or `Connected` with another endpoint, call `client.rejectConnection(endpointId)` and return. Exception: if the current state is `Pending(accepted=false)` for an unknown peer and the new peer's `installId == preferredPeerId` (a new `@Volatile var preferredPeerId: String?`), reject the old one and take the new one.
- **Pending timeout.** Arm a main-thread timeout (`Handler`) of `PENDING_TIMEOUT_MS` on each `Pending`. If the same `Pending` is still current when it fires, call `disconnectFromEndpoint` and set `None`.
- **Local rejects.** `rejectPending()` records the endpoint in `rejectedLocally`. The `STATUS_CONNECTION_REJECTED` handler sets `_error = "Camera declined"` only when the endpoint is not in `rejectedLocally`.
- **Error messages.** `fail()` uses `linkErrorMessage`.
- **Stale `stopAll`.** Add `generation: Int`, incremented in `startAdvertising`, `startDiscovery` and `connect`, and `fun stopAllIf(gen: Int) { if (gen == generation) stopAll() }`.
- **Payloads.** `payloads.onPayloadReceived` STREAM: if `localRole == Role.Camera` (set it in `startAdvertising` and `connect`), call `client.cancelPayload(payload.id)` and return.

### Phase B: CameraSession / CameraController

**B1. Outbox and ownership (D2, D3).** Replace `pendingPhotos` and `shots` with `PhotoOutbox` and `ShotRegistry`.
- `capture()` adds entries with `owner = sessionRemoteId ?: ""`. With no owner, it saves locally only and queues nothing.
- On `Connected`, if `c.peer.installId == sessionRemoteId`: `outbox.newLink()`, then flush.
- `flushPhotos()` sends each `due(owner)` entry, then calls `markSent`.
- `onCommand(PhotoAck)` calls `outbox.ack(id)`.
- `endSession()` and `stop()` call `outbox.clear()` and `registry.clear()`.

**B2. Trust and re-entry (D1, D6).**
- `autoAccept = { peer, _ -> peer.role == Role.Remote && peer.installId.isNotEmpty() && peer.installId == sessionRemoteId }`, whatever the safe-mode setting.
- On `Connection.None` (not ended): `link.preferredPeerId = sessionRemoteId`, and start `reentryJob = launch { delay(REENTRY_WINDOW_MS); sessionRemoteId = null; link.preferredPeerId = null; outbox.clear(); registry.clear() }`. Cancel it on `Connected`.
- Chimes play always, never gated on safe mode: a new Remote gets `beeper.chime(up = true)`; a re-entry gets a new `beeper.softChime()` (`TONE_PROP_BEEP`, 120 ms). An end gets `chime(up = false)`.
- Store `sessionStartedAt` on the first `Connected` of a session (not on re-entry), and reset it in `endSession`/`stop`.

**B3. Activity, cap, clamp (D5).**
- `onCommand` sets `lastActivity` only if `countsAsActivity(cmd)`. When a command resets activity while `_idleLeft` is ≤ `WARNING_SECONDS`, call `sendStatus()` immediately (R23).
- Session cap, in `checkIdle()`, only when `prefs.safeMode`:
  - `cap = capLeft(now - sessionStartedAt, SESSION_CAP_MS, CAP_GRACE_MS)`.
  - While busy (countdown active or `_saving`), don't advance: shift `sessionStartedAt` forward by the elapsed tick.
  - Expose `capLeft: StateFlow<Int>`; chime once on entering grace.
  - At 0, call `endSession("Session limit reached")`.
- `fun continueSession() { sessionStartedAt = now }`.
- `sendStatus` includes `capLeft`, `sessionSec` and `muted`.

**B4. Shutter and lens (D18).**
- `shutter()`: if `countdownJob?.isActive == true`, send `ShutterRejected("Still saving the last photo")` and return. The timer is already clamped by the protocol.
- `SetLens`: if the countdown is active or `_saving`, or less than 1 s has passed since the last applied `SetLens`, don't switch. Call `sendStatus()` so the Remote reverts its optimistic lens.
- `Focus`: ignore if less than 100 ms since the last one.

**B5. Capture robustness (D5, D12, D22).**
- Wrap each `controller.takePicture(f)` in `withTimeoutOrNull(SHOT_TIMEOUT_MS) { … } ?: false`, and the whole capture body in `withTimeoutOrNull(CAPTURE_TIMEOUT_MS)`. `_saving` resets in `finally`, which is kept.
- If `saveToGallery` returns null:
  - move `best` to `filesDir/unsaved/<fileName>.jpg`;
  - send `ShutterRejected("Couldn't save on the Camera")`;
  - don't set `_lastSaved`, don't queue, don't send `Captured`.
- Delete the burst files in `finally`, except a moved `best`.
- In `start()`, delete any `cacheDir/shot-*.jpg`.

**B6. Delete (D8).** `Delete(id)`: `entry = registry.takeIfOwned(id, currentPeerId)`. If present, delete the file (`ok = result`); otherwise `ok = false`. Reply `Deleted(id, ok)`. Emit `remoteDeleted` (a SharedFlow) when `ok`, for the Camera notice.

**B7. Alarm floor (D9).** New `media/AlarmGuard.kt`:
- `engage()` in `start()`: if the alarm volume is below `ceil(max * 0.5)`, set it to that in `runCatching` and remember the original.
- `muted = volume == 0 || (API ≥ 23 && nm.currentInterruptionFilter == INTERRUPTION_FILTER_NONE)`.
- `release()` in `stop()` restores the original only if the current volume still equals the value we set.

**B8. Controller and teardown (D11, D22).**
- `CameraController.attach` adds a lifecycle observer on `owner`: `ON_DESTROY` → `_bound.value = false; camera = null`.
- `CameraSession.stop()` captures `val gen = link.generation` and posts `link.stopAllIf(gen)` instead of `if (scope == null) link.stopAll()`.
- Add `val isRunning get() = scope != null`.
- Add `fun retryAdvertising() { link.clearError(); link.startAdvertising(Role.Camera) }`.

**B9. Notification (D17).**
- `SessionService.update(context, role, text)` calls `startForegroundService` with `EXTRA_TEXT`, and `onStartCommand` uses that text.
- Add an action "Stop" (`PendingIntent.getService`, `ACTION_STOP`). On it, a Camera role calls `(application as PoseApp).cameraSession.endSession("Stopped on the Camera")`.
- `CameraSession` calls `update` on `Connected` with "LIVE · <name>" and on `None` with the existing waiting text.

### Phase C: RemoteSession

**C1. Photo intake (D4, D3).**
- State: `requested` (Int), `captured` (Set), `received` (Set).
- `requested++` whenever a `Shutter` is actually sent, directly or from the queue. `Captured(id)` adds to `captured`.
- For each `link.photos` item:
  - If `!acceptPhoto(...)` and the id is in `received`, send `PhotoAck(id)` (a duplicate); otherwise drop it.
  - Else require the JPEG magic `FF D8`, bounds-decode, and require the long side ≤ 8192 and `store.hasSpace()`.
  - Then save, decode the thumb with the new `PhotoStore.decodeScaled(bytes, 1080)` (`inSampleSize`, then `createScaledBitmap` to ≤ 1080 long side), wrapped in `runCatching`.
  - Add to `received`, send `PhotoAck(id)`, clear `_awaitingPhoto`.
- `_shots` keeps at most `MAX_SHOTS = 12`, dropping the oldest from the list (they stay in the gallery), and is cleared in `stop()`.
- Every failure path clears `_awaitingPhoto`.
- Awaiting timeout: when `_awaitingPhoto` becomes true, start a job of `AWAIT_PHOTO_TIMEOUT_MS`. When it fires, clear the flag and emit the notice "Photo saved on Camera".

**C2. Frames (D13).** Before decoding a frame, bounds-decode it and skip it if the long side is over 2048.

**C3. Reconnect (D6, D19).** Rewrite `beginReconnect()`:
- Loop until the deadline (30 s):
  - ensure discovery;
  - wait for `lastPeerId` in `discovered`;
  - `connect`;
  - wait up to `min(remaining, 8 s)` for `Connected` or `None`;
  - on `None`, `delay(1_500)` and loop.
- Change `LinkPhase.Reconnecting` to `(secondsLeft, awaitingAllow: Boolean)`. `awaitingAllow` is true while `link.connection` is `Pending(accepted = true, code.isNotEmpty())`. The first time it becomes true, extend the deadline to at least now + 30 s.
- Don't clear `_countdown` when a drop happens mid-countdown. If `shutter()` is tapped while `Reconnecting` and `_countdown != null`, set `queuedCancel = true` and emit the notice "Cancel queued". `onReconnected` sends `CancelCountdown` first, then any queued shutter, which now also arms the watchdog.
- `Deleted(id, ok = false)` → notice "Camera copy kept".
- `keepAlive()` also sets `_status.update { it?.copy(idleLeft = -1) }`.

**C4. Delete with undo (D8).**
- `scheduleDelete(shot, alsoCamera)` removes the shot from `_shots` into `pendingDelete` and launches a job with `delay(5_000)`. The job then deletes locally and, if `alsoCamera`, sends `Delete(id)`; on send failure it emits "Camera copy kept".
- `undoDelete()` cancels the job and restores the shot at its index.

### Phase D: UI

**D-a. `ui/AppRoot.kt` and manifest (D11).**
- `screen` uses `rememberSaveable(stateSaver = …)`, mapping each `Screen` to a string and back.
- Initial value: `cameraSession.isRunning` → `Camera`; `remoteSession.isRunning` → `Remote`; otherwise the current rule.
- `MainActivity` `configChanges` adds `smallestScreenSize|density|fontScale|locale|layoutDirection|keyboard|navigation`.
- Remove the pairing auto-connect effect. `suppressAutoConnect` becomes unused; delete it.

**D-b. `ui/remote/RemoteScreen.kt` (D10, D15, D18, D22, D26).**
- `BackHandler(enabled = reviewing != null) { reviewing = null }`.
- `DisposableEffect(reviewing == null)`: add `FLAG_SECURE` while the live view shows, and clear it otherwise and on dispose.
- `DisposableEffect(phase is Live)`: `screenBrightness = 1f` while Live; restore `BRIGHTNESS_OVERRIDE_NONE`.
- `LensPicker` gets an `enabled` param, false while `counting || awaiting`.
- `volumeShutter` is non-null only when `(phase is Live || phase is Reconnecting) && reviewing == null`.
- `ReconnectVeil`: when `awaitingAllow`, show "Tap Allow on the Camera" instead of "Tap the shutter to queue a shot".
- Notices:
  - `status.muted` → "Camera is muted";
  - `status.capLeft >= 0` → "Continue on the Camera · ${capLeft}s";
  - `status.safeMode == false` → "Safe mode off" (Warn).
- The notices column and `IdleWarning` get `semantics { liveRegion = LiveRegionMode.Polite }`.

**D-c. `ui/remote/ReviewScreen.kt` (D8, D23).**
- Trash opens a small sheet with **"Delete here"** (primary), **"Delete on both"**, and "Cancel". The pick calls `scheduleDelete`. `RemoteScreen` shows an Undo snackbar pill "Deleted · Undo" for 5 s.
- Add a Share icon ("Share") next to trash. It is visible only when `shot.uri?.scheme == "content"` (API ≤ 28 gives `file://`, which would throw). It sends `ACTION_SEND`, `image/jpeg`, `EXTRA_STREAM`, `FLAG_GRANT_READ_URI_PERMISSION`, via `createChooser`.
- Caption "On both phones" → remove.

**D-d. `ui/camera/CameraScreen.kt` (D1, D9, D14, D16, D21, D20).**
- **LIVE badge.**
  - Condition: `connected` only (remove `|| !dimmed`).
  - Text: "LIVE · name · m:ss", using `sessionSec`, which the Camera computes locally from `sessionStartedAt`.
  - Next to it, a **"Stop"** chip → `session.endSession("Stopped on the Camera")`, drawn above the lock and dim overlays.
- **Cap card.** When `capLeft >= 0`, a card above the overlays: "Still OK?" with a **"Continue"** button → `session.continueSession()`.
- **Safe mode off.** Show a chip "Safe mode off" (Warn) under LIVE when `!safeMode`.
- **Sound.** If `muted`, show a NoticePill "Sound off".
- **Remote delete.** On `remoteDeleted`, show a NoticePill "Deleted by Remote" for 2 s.
- **Capture blink.** Move the blink `Box` after the dim and lock overlays, so it draws above them.
- **Dim.**
  - Connected: dim after 10 s.
  - Not connected, not Pending, and (not ended or ended): dim after 60 s.
  - Brightness when dimmed is `0.12f` if `connected && safeMode`, else `0.01f`.
- **Back.** `BackHandler(enabled = locked) {}` swallows Back while locked.
- **X / Leave during a shot.** If `countdown != null || saving`, show a confirmation: "Photo in progress" with [Stay] [Leave].
- **Overlays.** In the `DisposableEffect`: on API 31+, `activity.window.setHideOverlayWindows(true)` (manifest: `android.permission.HIDE_OVERLAY_WINDOWS`), plus `LocalView.current.filterTouchesWhenObscured = true`. Reset both on dispose.
- **Advertising failure.** In `WaitingCard` stage 0, when `!advertising && linkError != null`, show a PrimaryButton "Try again" → `session.retryAdvertising()`.
- **Stage 1 ("Same code?").** Add SecondaryButton "Cancel" → `session.declineRemote()`.

**D-e. `ui/pairing/RemotePairingScreen.kt` (D6, D20).**
- The known Camera (`installId == lastPeerId`) sorts first, and its row reads "Reconnect" instead of "Paired before". There is no automatic connect.
- When the error is set, or when `slow` and no cameras have been found, show "Try again" → `link.stopDiscovery(); link.startDiscovery()`.
- If `Build.VERSION.SDK_INT <= 30` and `!LocationManagerCompat.isLocationEnabled`, show NoticePill "Turn on Location". Tapping it opens `ACTION_LOCATION_SOURCE_SETTINGS`.
- If `BluetoothAdapter` is disabled, show "Turn on Bluetooth", which opens `Settings.ACTION_BLUETOOTH_SETTINGS`.
- Both checks also run on the Camera screen.
- Note: the title "Finding\nCamera…" is **unchanged**; `SmokeTest` asserts it.

**D-f. `ui/settings/SettingsScreen.kt` (D1, D27).**
- **Turning safe mode off** opens a confirm sheet:
  - title "Turn off safe mode?";
  - body "No session limit. Allow, LIVE and chimes stay on.";
  - buttons "Cancel" / **"Turn off"**.
- Turning it on applies immediately.
- The idle chips no longer include "Off".
- **About section:**
  - "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" (`buildConfig = true` is already enabled);
  - "Privacy policy" opens `R.string.privacy_policy_url` (placeholder `https://holdthatpose.app/privacy`, and **the owner must replace it before release**);
  - "Licences" opens a simple text dialog: Instrument Serif and Manrope (SIL OFL 1.1), AndroidX and CameraX (Apache 2.0), Google Play services (Google APIs ToS).

**D-g. Components and theme (D24, D25, D26).**
- `pressable` gets `.minimumInteractiveComponentSize()`.
- `GlassIconButton` with `active` adds `semantics { stateDescription = if (active) "On" else "Off" }`.
- `LensPicker` segments use `Modifier.selectable(selected, role = Role.RadioButton)`.
- Settings chips use `selectable`.
- `CodeDigits`: digit `fontSize = base.sp / LocalDensity.current.fontScale`, with tiles `heightIn(min = …)`.
- `PermissionScreen` and the `WaitingCard` body get `verticalScroll`. Replace any `weight` spacer inside them with `Spacer(Modifier.height(24.dp))`. Home stays as it is; its scroll is LATER.
- `PoseColors.PaperFaint` alpha 0.38 → 0.55.

**D-h. `res/xml/data_extraction_rules.xml` (D28).** Exclude `sharedpref` `holdthatpose.xml` from both `<cloud-backup>` and `<device-transfer>`. Reference it with `android:dataExtractionRules` in the manifest (`tools:targetApi="31"`).

**D-i. README.**
- Correct "can't be muted" → "played on the alarm stream; the Camera raises it and warns when muted".
- Update the safe-mode table:
  - Allow, LIVE and chimes are always on;
  - the 30-min cap applies in safe mode only;
  - idle maximum is 10 min when safe mode is off;
  - delete is here-or-both with Undo.

### Smoke-test impact (`app/src/androidTest/.../SmokeTest.kt`, must stay green)
- **Settings block.** "Off" no longer exists in either mode.
  - Replace `onNodeWithText("Off").performClick(); assertEquals(60, …)` with `onAllNodesWithText("Off").assertCountEquals(0)`.
- **Safe-mode switch block.** After clicking "Safe mode switch", click **"Turn off"** before `assertEquals(false, prefs.getBoolean("safe", true))`. Remove the two lines that click "Off" and assert `idle == 0`.
  - Without the "Off" click, `idle` stays 120 through the off/on toggle, so change the final `assertEquals(60, prefs.getInt("idle", 0))` to `assertEquals(120, …)`. "2 min" must still write 120.
- **Unchanged and still asserted:**
  - "STEP 01", "Next", "Skip", "Camera", "Remote", "Settings", "Safe mode", "Back";
  - "I agree", "Permissions", "Nearby devices", "Finding\nCamera…";
  - "Waiting for\nRemote", "Lock screen", "Hold to unlock".

  Don't rename these. The new "Stop" and cap UI only appear when connected, so `CameraSmokeTest` is unaffected. Remote screens are not screenshot-tested, so `FLAG_SECURE` doesn't affect CI.
- **`LogicTest.safeModeNeverAllowsOff`** changes as in A3.

## 4. LATER backlog

| ID / part | What | Why later |
|---|---|---|
| D29 | Session key + HMAC on reconnect; per-pair keys in Keystore; rotating advert tokens; "Start again" re-admit without Allow (P6) | Protocol/trust redesign; a bug breaks every reconnect; needs two-device tests |
| D30 | Editable display name; no owner name broadcast by default | Changes pairing identity and display; product decision on the default |
| D31 | Bitmap reuse for preview frames (R17) | Perf work that needs on-device profiling; risk of preview regressions |
| D32 | "Session ended" marker in the endpoint name; pause idle when pings stop (R19) | Advert format change; half-open states only reproducible on hardware |
| D33 | Real release signing key (S18) | CI secrets and a key-management decision, not app code |
| D34 | Full-res fetch (`RequestFull`), pinch zoom, recent-photos strip (P4.2–4) | Features; the transfer path needs devices; Share covers the core need |
| D35 | Settings sheet during a session (P13) | New UI surface plus live re-application of settings |
| D36 | Extract all strings; Hebrew; `supportsRtl` (P15) | Large mechanical sweep; its own pass |
| D37 | Play Console: FGS video, target audience 13+/18+, Data safety (P16) | Store-listing work, not code; before submission |
| D38 | Help/FAQ, "Send feedback" (P18) | Content work; reuse the D20 checks later |
| D39 | Disk-persisted outbox; persisted id→uri map (R1, R14) | Needs two-device testing; a filename-based lookup was rejected (lets a Remote delete arbitrary photos) |
| D1 part | Device-credential gate to turn off safe mode (S3) | Emulator-untestable path; the confirm sheet covers accidental use |
| D7 part | Block list and per-peer decline cooldown (S11) | Nice to have after the overlap rejection and pending timeout |
| D8 part | Camera-side "Remote may delete here" setting (S13) | Owner-scoping plus Undo covers the main risk |
| D13 part | Map `ShutterRejected`/`SessionEnded` reasons to local codes (S15) | Touches every message; low severity once D29 lands |
| D16 part | LIVE full-brightness pulse; bigger badge | Needs tuning on a real panel |
| D21 part | Release the camera and stop advertising after 10 min unconnected or ended (R15) | CameraX re-bind on wake needs device verification |
| D25 part | Home screen scroll at large fonts; countdown numeral ÷ fontScale; `heightIn` sweep (P8) | Home layout uses weights; screenshot-verified; separate UI pass |
