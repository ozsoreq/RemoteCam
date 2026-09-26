# QA 02 — Reliability & edge-case flows ("Breaker")

Scope: connection lifecycle, threading/state, CameraX, resources, UX dead-ends. I read every
file under `app/src/main/java/app/holdthatpose/` and traced each flow below through the code.
Line references are to the current tree. This review made no code changes.

## Findings table

| ID | Title | Where | Sev |
|---|---|---|---|
| R1 | Queued review copies outlive the session and go to whichever Remote connects next (a different person). They are lost on process death. | `CameraSession.pendingPhotos`, `flushPhotos`, `stop`, `endSession` | **High** |
| R2 | A photo is dropped from the queue when sending *starts*. A link drop mid-transfer loses it or saves a truncated JPEG on the Remote. | `CameraSession.flushPhotos`, `NearbyLink.sendPhoto`, `Protocol.readPhotoStream` | **High** |
| R3 | The Remote keeps an ~8 MB full-size bitmap per photo forever, so it crashes with OOM after about 25–30 shots | `RemoteSession._shots`, `PhotoStore.decodeThumb(…,1600)` | **High** |
| R4 | System Back while reviewing photos disconnects the whole session | `AppRoot` `BackHandler`, `RemoteScreen` review overlay | **High** |
| R5 | Recreating the activity (split-screen, font/display size, language, fold) resets the UI to Home while the session and link stay live. The Camera shows no LIVE badge and CameraX is unbound but `bound` still reads true. | `AppRoot` `remember{screen}`, manifest `configChanges` | **High** |
| R6 | When the Camera declines a known Remote, the Remote auto-connects again at once, so the Allow prompt loops | `RemotePairingScreen` auto-connect effect | Medium |
| R7 | Reconnect makes one connect attempt only, and a Camera restart needs **Allow**, which the Remote never mentions. Result: "Lost" after 30 s. | `RemoteSession.beginReconnect` | Medium |
| R8 | The Camera silently ignores the shutter while the previous burst is still saving. The Remote shows a frozen countdown and no photo is taken. | `CameraSession.shutter` | Medium |
| R9 | Tapping the shutter to cancel during a link drop queues a *new* shot. The running countdown can't be cancelled. | `RemoteSession.beginReconnect` / `shutter` | Medium |
| R10 | "Saving photo…" and the thumbnail spinner stay on forever if the review copy never arrives | `RemoteSession._awaitingPhoto` | Medium |
| R11 | No timeout on `takePicture`. A lost CameraX callback hangs capture forever and disables idle auto-disconnect. | `CameraController.takePicture`, `CameraSession.checkIdle` | Medium |
| R12 | Failures to start advertising or discovery are never retried. The Camera sits on "Starting…" and the Remote on "Finding Camera…" for good. The message blames BT/Wi-Fi even when Play services is missing. | `NearbyLink.startAdvertising/startDiscovery/fail` | Medium |
| R13 | A gallery-save failure is reported as "Saved" and the full-res burst files are then deleted | `CameraSession.capture` | Medium |
| R14 | "Delete on both phones" silently keeps the Camera copy when the Camera doesn't know the id. The `Deleted` ack is ignored. | `CameraSession.onCommand(Delete)`, `RemoteSession.delete` | Medium |
| R15 | While unconnected or "Session ended", the Camera never dims and never stops the camera, so it drains the battery indefinitely | `CameraScreen` dim effect, `CameraController` stays bound | Medium |
| R16 | Leaving the Camera (X, Back — Back even when **locked**) has no confirmation and kills an in-flight countdown or burst | `AppRoot.navigate`, `CameraScreen` lock overlay | Medium |
| R17 | Both sides allocate new bitmaps for every preview frame (~50 MB/s of garbage on the Camera): GC jank, heat, battery drain | `CameraSession.onFrame`, `RemoteSession` frame decode | Medium |
| R18 | When the Camera declines a Remote, the Camera shows "The other phone declined the connection" permanently | `NearbyLink.onConnectionResult`, `CameraScreen` | Low |
| R19 | If the Camera ends the session idle while the Remote thinks the link dropped, the Remote says "Move closer" and retries can never succeed | `CameraSession.endSession`, `RemoteSession` | Low |
| R20 | `CameraSession.stop()`'s delayed `stopAll()` can kill the discovery of a Remote session started within 350 ms | `CameraSession.stop` | Low |
| R21 | The Camera's "Same code?" state (Allowed, waiting for the Remote) has no cancel button and no timeout. Pending approval never expires. | `CameraScreen.WaitingCard`, `NearbyLink` | Low |
| R22 | Lens switching isn't blocked during a countdown or burst. A rebind mid-burst fails frames, and bind errors send `ShutterRejected`, which wipes the Remote's countdown. | `CameraSession.onCommand(SetLens)` | Low |
| R23 | After "Keep going" the idle warning can linger for up to 2 s (status is throttled) | `CameraSession` status loop | Low |
| R24 | Volume keys are swallowed even when the Remote is Lost or Ended, and they fire the shutter from inside Review | `MainActivity.onKeyDown`, `RemoteScreen` | Low |
| R25 | Burst temp files (`cacheDir/shot-*.jpg`) are never swept after a crash or kill mid-capture | `CameraSession.capture` | Low |
| R26 | With safe mode off, any Remote is auto-accepted during the reconnect window and takes over the Camera | `CameraSession.start` `autoAccept` | Low |

---

## Details

### R1 — Queued photos leak to the next Remote and die with the process (High)
**Code.** `pendingPhotos` (`CameraSession.kt:99`) is a field of the app-scoped singleton. `stop()`,
`endSession()` and `restart()` never clear it. `flushPhotos()` runs on *every*
`Connection.Connected` (`:138`) and doesn't check who the peer is. It is memory only.

**Repro.**
1. Remote A starts a 10 s timer, then walks out of range (or A's phone dies). The Camera captures, and the review copy is queued.
2. A never comes back. The Camera owner taps **Start again**, or leaves and re-enters Camera, and pairs friend B's phone with Allow.
3. B receives A's photo, and B's review can **Delete** it from the Camera gallery via its id in `shots`.

Variant: swipe the Camera app away (`stopWithTask=true`) before A reconnects, and the review copy is gone.

**Expected.** Queued copies go only to the Remote that was in session, and survive a restart.
**Actual.** They go to any next Remote, or are lost.

**Fix.** Key the queue by `sessionRemoteId` (store `remoteInstallId` with each entry). Flush only when
`c.peer.installId` matches, and clear entries for other peers when a new Remote is allowed.
Persist the queue to `filesDir/outbox/` (JPEG + JSON header) and reload it in `start()`. Expire
entries after N hours.

### R2 — Photo removed from the queue before delivery (High)
**Code.** `flushPhotos()` calls `pendingPhotos.removeFirst()` as soon as `sendPhoto()` returns
`true` (`CameraSession.kt:341-342`). `sendPhoto` only *enqueues* a STREAM payload. On the Remote,
`readPhotoStream` does `data.readBytes()` until EOF, and the header has no byte length or checksum.

**Repro.** On Bluetooth (a ~1 MB copy takes several seconds), take a photo, then walk away while
"Saving photo…" shows.

**Actual.** The Camera has already dropped the copy, so nothing is resent on reconnect. The Remote either
gets an exception (the photo is silently dropped, see R10) or a truncated stream. A truncated stream is
saved to the gallery as a grey-bottomed JPEG and shown in Review as if it were fine.

**Fix.**
- Add `len` (and a CRC32) to `PhotoHeader`. The Remote verifies both, and saves and acks only if they match.
- Add `Cmd.PhotoAck(id)`. The Camera removes an entry only on ack, or on `PayloadTransferUpdate.SUCCESS` for that payload id.
- Resend unacked entries on reconnect. The Remote de-duplicates by id.

### R3 — Remote OOM from unbounded full-size bitmaps (High)
**Code.** Every received photo is decoded with `decodeThumb(jpeg, 1600)` (`RemoteSession.kt:171`).
For a ~1633×1225 review copy, `sampleSizeFor` returns 1, so the full bitmap is about 8 MB, and it is
prepended to `_shots` forever. `stop()` doesn't clear `_shots`, so the list grows across sessions and
Cameras. `largeHeap` isn't set.

**Repro.** Take about 25–40 photos in one process lifetime on a mid-range phone (heap limit 192–256 MB).

**Actual.** `OutOfMemoryError` in the `photos` collector (uncaught, no handler), which crashes the Remote.
Frame bitmaps (R17) make it worse.

**Fix.** Keep only `Shot(id, uri, takenAt)` plus a small thumbnail (≤ 256 px) for the deck. Decode
full-screen images on demand in the pager with an `LruCache` (sized from `maxMemory/8`), or use
Coil or Glide with the gallery `uri`. Wrap decodes in `runCatching`. Clear or scope `_shots` per Camera
session.

### R4 — Back in Review disconnects the session (High)
**Code.** The only `BackHandler` is in `AppRoot` (`:85`). On `Screen.Remote` it navigates to
`RemotePairing`, which runs `remoteSession.stop()` → `link.stopAll()`. The Review overlay (`RemoteScreen.kt`,
the `reviewing` state) registers no back handler.

**Repro.** Remote → take a photo → tap the thumbnail → press or swipe Back.
**Expected.** Review closes. **Actual.** The link is torn down (no goodbye is sent) and the user lands on pairing.

**Fix.** Add `BackHandler(enabled = reviewing != null) { reviewing = null }` in `RemoteScreen`.
Also consider a confirm sheet ("Disconnect from Camera?") for Back on a Live Remote.

### R5 — Activity recreation orphans the session (High)
**Code.**
- `var screen by remember { … }` (`AppRoot.kt:49`) isn't saveable.
- The manifest `configChanges` lacks `smallestScreenSize|density|fontScale|locale|layoutDirection|keyboard|navigation`.
- The sessions are app-scoped and aren't stopped when the activity is destroyed.
- CameraX is bound to the destroyed Activity's lifecycle. CameraX unbinds, but `CameraController._bound` stays `true`.

**Repro.**
1. The Camera is Live with a Remote.
2. On the Camera phone, enter split-screen, change the font or display size, change the language, or unfold a foldable.

**Actual.**
- The Camera phone shows **Home** while the link is still Connected. The **LIVE** badge (a safe-mode guarantee) is gone.
- The Remote's preview freezes.
- A shutter from the Remote gets "The camera couldn't take the photo", because `bound` is stale and the use case is unbound.
- The idle timer keeps running.
- On the Remote the same thing happens: Home is shown while pings keep going.

**Fix.** Keep `screen` in `rememberSaveable` (with a `Saver`) or in a ViewModel, and derive the initial
screen from the live session (`cameraSession.isRunning` → Camera, `link.connection is Connected` +
remote scope → Remote). Add the missing `configChanges`. Also observe `ON_DESTROY` in `CameraController` to
set `_bound=false`, or bind to a controller-owned `LifecycleRegistry`.

### R6 — Decline loops on a known Remote (Medium)
**Code.** `RemotePairingScreen` `LaunchedEffect(discovered, connection)` auto-connects to
`lastPeerId` whenever `connection` becomes `None` and `suppressAutoConnect` is false. That is the default when
you enter from Home. A fresh Camera session in safe mode has `sessionRemoteId=null`, so it prompts.

**Repro.** Both phones open (Remote from Home). The Camera shows "Allow this Remote?" → **Decline**.
**Actual.** The Remote gets REJECTED → `None` → reconnects at once → the Camera prompts again, indefinitely.

**Fix.** On `STATUS_CONNECTION_REJECTED`, set `suppressAutoConnect = true`, or apply a per-peer
backoff (for example, don't auto-connect again for 60 s). Show "Camera declined" on the Remote.

### R7 — Reconnect is one-shot and blind to the approval gate (Medium)
**Code.** `beginReconnect()` (`RemoteSession.kt:329-363`) discovers the peer, calls `link.connect` once,
then only waits for `Connected`. If `requestConnection` fails (common with Nearby's
`STATUS_ENDPOINT_IO_ERROR` / `STATUS_RADIO_ERROR`) or `onConnectionResult` fails, `connection` goes back to
`None` and nothing retries until the 30 s deadline.

If the Camera app was restarted, `sessionRemoteId` is null, so safe mode needs **Allow**. The Remote's
veil still says "Reconnecting… Tap the shutter to queue a shot", and the queued shot is discarded when the deadline passes.

**Fix.** Loop inside the window: on `None`, back off 1–2 s, then restart discovery and connect. Surface
`Connection.Pending(accepted=true, code≠"")` as "Tap Allow on the Camera". Extend the window while
approval is pending.

### R8 — Shutter silently dropped while saving (Medium)
**Code.** `CameraSession.shutter` returns silently when `countdownJob?.isActive` is true (`:265`), and the job
stays active through the whole burst, sharpness pass, save and review-copy step (2–4 s).

**Repro.** Timer 0, burst on. Tap the shutter, then tap again about 1 s later.
**Actual.** The Remote shows an optimistic countdown ("0", or "3" frozen for up to 9 s until its watchdog fires).
No photo is taken and there is no message.

**Fix.** Send `ShutterRejected("Still saving the last photo")`, or better, queue one pending shutter
and run it once `_saving` clears. Separate the countdown job from the capture job, so "busy" means
"countdown running".

### R9 — Cancel during a drop becomes an extra shot (Medium)
**Code.** `beginReconnect()` sets `_countdown.value = null`. On the Camera the countdown keeps going (by
design). A tap on the shutter, which the user meant as "cancel", now finds no countdown, and because the phase is
`Reconnecting` it **queues a new Shutter**. That shutter fires after reconnect, or is silently ignored (R8).

**Fix.** Keep the last countdown value while Reconnecting and show "Camera is still counting". Queue
`CancelCountdown` instead of `Shutter` when a countdown was running at the drop.

### R10 — "Saving photo…" never clears (Medium)
**Code.** `_awaitingPhoto` is set on `Countdown(0)` or `Captured`, and cleared only when a photo arrives,
on `ShutterRejected`, or on `stop()`. It stays on when:
- the stream fails (R2);
- `PhotoStore.reviewCopy` returns null on the Camera (`Captured` is sent, but no photo follows);
- `decodeThumb` returns null (`return@collect` before the clear).

**Fix.** Track awaited ids, and time out after about 20 s plus the Bluetooth allowance with "Photo saved on Camera,
copy will sync". Send `Cmd.PhotoUnavailable(id)` when `reviewCopy` fails. Clear the flag in the
null-decode path too.

### R11 — No capture watchdog (Medium)
**Code.** `takePicture` is `suspendCancellableCoroutine` with no timeout, running under
`NonCancellable`. If CameraX never calls back (known on some HALs with rapid
`MINIMIZE_LATENCY` bursts and rebinds), `_saving` stays `true` forever. The consequences:
- `checkIdle` treats that as activity, so the safe-mode auto-disconnect **never fires**;
- every later shutter is ignored (R8).

**Fix.** Wrap each `takePicture` in `withTimeoutOrNull(5_000)` and the whole capture in about 15 s.
Always reset `_saving` and `_countdown`.

### R12 — Link start failures are terminal and mislabelled (Medium)
**Code.**
- `startAdvertising` failure sets `_advertising=false` plus `_error`. It is retried only on a later `Connection.None` transition, which never comes.
- `CameraScreen` shows "Starting…" (amber) and the error pill forever. The error is never cleared on the Camera.
- `startDiscovery` failure: the pairing screen clears the error after 4 s, but the radar keeps spinning, and after 8 s it says "Open the app → Camera on the other phone" (misleading).
- `fail()` adds "check Bluetooth and Wi-Fi are on" for *any* `ApiException`, including `API_UNAVAILABLE` / `SERVICE_MISSING` (no Play services), `MISSING_PERMISSION` and `STATUS_BLUETOOTH_ERROR`.

**Repro.** Turn Bluetooth off, open Camera, then turn Bluetooth on. Nothing recovers.

**Fix.**
- Retry with backoff, and register for `BluetoothAdapter.ACTION_STATE_CHANGED` / Wi-Fi changes to retry.
- Add a "Try again" button on the card.
- Check `GoogleApiAvailability.isGooglePlayServicesAvailable` before choosing a role.
- Map status codes to specific messages.

### R13 — Failed gallery save reported as saved (Medium)
**Code.** `capture()` sends `Captured` and sets `_lastSaved` ("Saved to gallery" toast) even when
`saveToGallery` returned `null`, then deletes all burst files, so the full-res image is gone. Delete from the
Remote is then a no-op (R14).

**Fix.** If `uri == null`, keep `best` in `filesDir/unsaved/`, retry once, and send
`ShutterRejected("Couldn't save on the Camera phone")` or a distinct status. Delete temp files only after a successful save.

### R14 — "Delete on both phones" can silently keep the Camera copy (Medium)
**Code.** Camera: when `shots.remove(id)` is null (the Camera app was restarted, `shots` is memory only) or
`store.delete` fails, it still replies `Deleted(id)`. Remote: `Deleted` falls into `else -> Unit`, and the
only notice is shown when the send itself fails.

**Repro.** Take a photo, kill and restart the Camera app, reconnect, then delete that photo from Review on the Remote.
The Remote says nothing, and the photo stays on the Camera.

**Fix.** Persist the id→uri map (or look it up in MediaStore by `DISPLAY_NAME` =
`fileName(takenAt)`). Reply `Deleted(id, ok)`. On the Remote, show "Camera copy kept" when `ok=false` or when no
ack arrives within 5 s. Queue deletes while disconnected.

### R15 — Unattended Camera drains the battery (Medium)
**Code.** The dim effect returns early when `!connected` (`CameraScreen` `LaunchedEffect(lastTouch, connected, countdown)`).
`FLAG_KEEP_SCREEN_ON` is always set, and the CameraX preview plus analysis stay bound whether or not a Remote is
connected, including in "Session ended · hidden".

**Repro.** The idle timeout ends the session, or the Remote walks away for good. The Camera phone stays at full
brightness with the sensor running until the battery dies.

**Fix.** Dim after 10 s in every state. When not connected, unbind `ImageAnalysis`, or pause the
camera entirely after N minutes. After, say, 10 min unconnected or ended, clear `KEEP_SCREEN_ON` and stop
advertising.

### R16 — Accidental exit kills the shot (Medium)
**Code.** The Camera has two exits: the X button, and system Back via the `AppRoot` BackHandler. System Back works even
while `locked`, because `LockOverlay` only swallows touches. Either exit runs `cameraSession.stop()` at once, which calls
`controller.detach()` → `unbindAll()`. During a burst the in-flight `takePicture` calls fail with CAMERA_CLOSED and the
photo is lost, and the Remote gets `SessionEnded("The Camera was closed")`.

**Fix.** Disable the BackHandler while `locked`, which is what "lock" implies. When connected, or while a countdown
or capture is running, ask for confirmation, or at least defer `detach()` until `_saving` is false.

### R17 — Per-frame allocations (Medium, perf and battery)
**Code.**
- Camera `onFrame`: `image.toBitmap()` (1.2 MB) plus a rotated `createBitmap` (1.2 MB) plus `toByteArray()` plus the `encodeFrame` copy, for each frame at 20 fps, which is about 50 MB/s of garbage on the analysis thread.
- Remote: `decodeByteArray` makes a new bitmap for every frame, never recycled or reused.

**Fix.**
- Camera: reuse one ARGB bitmap via `copyPixelsFromBuffer`, and send `rotationDegrees` in the frame flags so the Remote rotates at draw time (`graphicsLayer.rotationZ`) instead of reallocating.
- Remote: use `BitmapFactory.Options.inBitmap` with a double buffer.
- Both: `encodeFrame` could write into a reused buffer.

### R18 — Wrong, sticky decline message on the Camera (Low)
**Code.** `onConnectionResult` sets `_error = "The other phone declined the connection"` for any
`STATUS_CONNECTION_REJECTED`, including when *this* phone called `rejectPending()`. The Camera never clears
`link.error`.

**Fix.** Remember a `rejectedLocally` endpoint id, and skip the message for it. Clear `error` on the next
successful advertise or connect, or after a timeout, as pairing does.

### R19 — Half-open link plus idle end gives a misleading "Lost" (Low)
**Code.** Suppose the Remote detects a drop before the Camera does. The Camera still sees `Connected`, and its idle
timer ends the session. `SessionEnded` goes nowhere, and the Camera stops advertising. The Remote's
reconnect finds nothing → **Lost**, with "Move closer. Photos are safe." and a **Try again** button that can never succeed.

**Fix.** Pause the idle countdown when no Ping has arrived in the last 4 s (the Remote pings every 2 s). Advertise a
"session ended" marker in the endpoint name, such as a role code suffix, so a discovering Remote can show "Session
ended on the Camera".

### R20 — Delayed `stopAll()` race (Low)
**Code.** In `CameraSession.stop()` (`:237`), `postDelayed({ if (scope == null) link.stopAll() }, 350)` checks only the
*Camera* scope. Leave Camera and reach Remote pairing within 350 ms: the post fires and kills the discovery that
`startPairing()` just started. `LaunchedEffect(Unit)` doesn't restart it, so the screen shows "Finding Camera…" forever.

**Fix.** Use a generation counter in `NearbyLink`: capture it at `stop()` and skip `stopAll()` if anything has started since.
Alternatively, have `stop()` await the send and call `stopAll` synchronously from a coroutine.

### R21 — The Camera's approval states have no cancel or timeout (Low)
The "Allow this Remote?" card stays open until someone taps it. After **Allow**, stage 1 ("Same code?") has
no Decline or Cancel, so if the Remote never confirms, the Camera waits indefinitely. Nearby may eventually time out, but
the UI gives no way out except leaving. **Fix.** Add a Cancel button (`link.rejectPending()` /
`disconnect()`) and a 60 s pending timeout in `NearbyLink`.

### R22 — Lens switch during a countdown or burst (Low)
`SetLens` is applied immediately. A rebind mid-burst (`p.unbindAll()`) fails in-flight captures, and the
"best of 3" may mix lenses. If the bind fails, `onError` sends `ShutterRejected`, and the Remote treats that as a failed
shot: it clears the countdown and `awaiting` while the Camera keeps counting. **Fix.** Defer `SetLens` until
`countdownJob` is idle, and disable the `LensPicker` while counting. Use a separate `Cmd.Error` for lens failures.

### R23 — The idle warning lingers after "Keep going" (Low)
When the warning clears, the status loop sends only on even ticks (`CameraSession.kt:162`), so the Remote may show
"Disconnecting in 3s" for up to 2 s more. **Fix.** Call `sendStatus()` immediately in `onCommand` when the
command resets the timer, and clear `idleLeft` optimistically on the Remote in `keepAlive()`.

### R24 — Volume-key handling (Low)
`volumeShutter` stays set whenever `RemoteScreen` is composed. In Lost or Ended, the keys do nothing but are still
swallowed, so the user can't change the volume. Inside Review they take a shot. **Fix.** Set the handler only when
`phase is Live || Reconnecting` and `reviewing == null`.

### R25 — Temp burst files leak (Low)
`shot-<id>-<n>.jpg` files are deleted only on the success path. Process death mid-capture, or an exception in
`sharpness` or `reviewCopy`, leaves them behind. **Fix.** Use `try/finally` for the deletes, and sweep `cacheDir/shot-*` in
`CameraSession.start()`.

### R26 — Safe mode off: takeover during the reconnect window (Low)
With `safeMode=false`, `autoAccept` accepts any `Role.Remote`. While A is reconnecting, the Camera is advertising
again, and any other Remote that connects is accepted silently and receives A's queued photos (see R1). **Fix.** Even
outside safe mode, prefer `sessionRemoteId` for about 30 s after a drop, and chime on a new peer.

---

## Checked OK (no bug found)
- **Double-tapping Connect or auto plus manual connect.** `NearbyLink.connect` returns unless the state is `None`.
- **Capture survives a link drop at zero.** `withContext(NonCancellable) { capture() }`. Cancel at `countdown==0` is ignored.
- **A deliberate end on the Camera doesn't trigger a Remote reconnect.** `SessionEnded` → `Ended`, and `Connection.None` doesn't start reconnecting unless the phase is `Live`.
- **Stale "saving" on the Camera.** Reset in `finally`.
- **Remote shutter watchdog** (`timer+6 s`) prevents a permanent "cancel mode" on the direct path. The queued-shutter path has no watchdog, but Camera ticks or `ShutterRejected` recover it.
- **Screen flags.** `FLAG_KEEP_SCREEN_ON` and the brightness override are cleared in `onDispose`. Window brightness doesn't leak outside the app.
- **Background capture blocked.** `paused` on `ON_STOP`, and `takePicture` or `shutter` reject.
- **Lens bind failure** falls back to the previous lens and never leaves a dead pipeline.
- **Frame back-pressure** (one frame in flight, 1 s timeout) keeps commands flowing, and in-flight state resets on disconnect.
- **Volume key auto-repeat** is filtered (`repeatCount == 0`).
- **Portrait lock plus accelerometer quadrant** keeps captures upright. Rotation isn't a recreation trigger.
- **Idle timer** counts running countdowns and saves as activity, resets on reconnect, and ignores `Ping`.

## Missing capabilities that would materially improve reliability
1. **Durable, acknowledged photo outbox**: disk-backed, per-Remote, `len`+CRC in the header, `PhotoAck`, resend on reconnect (fixes R1, R2, R10).
2. **A real reconnect state machine** on the Remote: retries with backoff, an explicit "waiting for Allow" state, and a longer window while approval is pending (R7, R19).
3. **Session epoch or id** in `Hello` / `Status`, so each side can discard stale queued state and ids from earlier sessions (R1, R3, R14).
4. **Process- and config-survivable navigation**: a ViewModel or saveable screen state derived from the live sessions (R5).
5. **Watchdogs everywhere a callback can go missing**: `takePicture`, pending handshake, awaited photo, and delete ack (R10, R11, R14, R21).
6. **Environment checks with retry**: Play services, Bluetooth, Wi-Fi and location (API ≤ 31) state receivers, and a "Try again" action on both link screens (R12).
7. **Bounded image memory**: thumbnails plus an LRU cache or image loader, and bitmap reuse for preview frames (R3, R17).
8. **Unattended-Camera policy**: auto-dim in every state, release the camera when unconnected, and auto-close after N minutes (R15).
9. **Two-device test harness**: a fake `NearbyLink` (an interface plus an in-memory pair) so the flows above run as JVM tests: drop mid-transfer, drop mid-countdown, decline loop, restart during reconnect.
