# QA 01: Security, privacy and abuse review ("Sentinel")

Scope: every file in `app/src/main` (Kotlin sources, `AndroidManifest.xml`, resources) plus `app/build.gradle.kts`.
I checked each claim against the code. Paths below are relative to `app/src/main/java/app/holdthatpose/`.
Threat actors considered: (a) a **creep** who controls one or both phones and wants covert viewing or capture; (b) a **stranger** nearby running a stock or modified build of the app; (c) a **malicious peer** that sends crafted payloads.

## Findings summary

| ID | Title | Where | Severity |
|---|---|---|---|
| S1 | Trust is based on a self-asserted `installId`, and the Camera broadcasts its own. A spoofed Camera gets a zero-tap, code-less connection from the victim's Remote | `net/NearbyLink.kt` `localName`/`parsePeer`/`lifecycle`, `session/RemoteSession.kt` `startPairing`/`beginReconnect`, `ui/pairing/RemotePairingScreen.kt` auto-connect | **High** |
| S2 | The Camera silently re-accepts the "session Remote" for an unbounded time after a drop, with no chime, and trusts only the spoofable `installId` | `session/CameraSession.kt` `start()` autoAccept (l.118), Connected branch (l.132) | **High** |
| S3 | Turning safe mode off makes the Camera auto-accept **any** nearby Remote silently. It is also a one-tap "covert mode" (LIVE hidden when dimmed, no chime, no timeout) | `CameraSession.start()` l.118, `ui/camera/CameraScreen.kt` l.296, `ui/settings/SettingsScreen.kt` l.87 | **High** |
| S4 | Queued photos and deletable shot ids leak across sessions to a *different* Remote | `CameraSession` `pendingPhotos`, `shots`, `flushPhotos`, `stop`/`endSession` | **High** |
| S5 | The Remote saves any photo a Camera pushes, unrequested and unvalidated, to its public gallery. This allows image injection (cyber-flashing) and fills storage and memory | `RemoteSession.start()` photos collector l.168-176 | **Medium** |
| S6 | Photo STREAM body is read with no size limit on both roles, so a peer can exhaust memory. The Camera even accepts streams it never expects | `net/Protocol.kt` `readPhotoStream` l.73, `NearbyLink.payloads` | **Medium** |
| S7 | Auto-disconnect is easy to defeat: any command (or a huge `Shutter.timer`) counts as activity, and there is no maximum session length | `CameraSession.onCommand` l.245, `checkIdle` l.181, `shutter` l.275 | **Medium** |
| S8 | Covert live video: the Remote can screen-record or screenshot the live preview with no shutter sound, because `FLAG_SECURE` is not set | `ui/remote/RemoteScreen.kt` | **Medium** |
| S9 | The "always visible" LIVE badge is shown at 1 % backlight under an 86 % black scrim. Overlay apps can cover the Camera screen or tapjack **Allow** | `CameraScreen.kt` l.156-163, l.276, l.294-301 | **Medium** |
| S10 | "Can't be muted" cues are on `STREAM_ALARM`, so the alarm volume slider or Do Not Disturb silences them. A reconnect makes no sound at all | `media/Beeper.kt` l.13, `CameraSession` l.132 | **Medium** |
| S11 | Connection-request spam or overwrite: a new request replaces the pending one, with no rate limit and no block list. The Allow prompt shows a name the attacker chooses | `NearbyLink.onConnectionInitiated`, `CameraScreen.WaitingCard` | **Medium** |
| S12 | A stable `installId` and the real device name (often the owner's name) are broadcast over BLE the whole time the Camera waits | `NearbyLink.localName`, `deviceName` | **Low** |
| S13 | The Remote can delete the Camera owner's full-resolution originals with no Camera-side consent or undo, even while the Camera is backgrounded | `CameraSession.onCommand` `Delete` l.254 | **Low** |
| S14 | No rate limiting on Shutter or SetLens, so a peer can fill the Camera's storage and battery or thrash the lens | `CameraSession.shutter`, `CameraController.setLens` | **Low** |
| S15 | Input-validation gaps: timer unbounded or negative, `Focus` NaN, and peer-supplied strings shown in the UI | `Protocol.Cmd.fromJson`, `CameraSession.onCommand`, `RemoteSession.onCommand` | **Low** |
| S16 | The foreground notification is static and misleading ("waiting for your Remote" while someone is viewing) and has no Stop action | `session/SessionService.kt`, `res/values/strings.xml` | **Low** |
| S17 | `allowBackup="false"` without `dataExtractionRules`: on Android 12+, device-to-device transfer still clones `installId`/`lastPeerId` | `AndroidManifest.xml`, `data/Prefs.kt` | **Low** |
| S18 | The release APK is signed with the debug key, so the integrity of sideloaded copies can't be trusted | `app/build.gradle.kts` `release.signingConfig` | **Low** |

---

## Details

### S1: Spoofed Camera hijacks the victim's Remote with zero taps and no code (High)
**Code.** `NearbyLink.localName()` advertises `C|<installId>|<deviceName>` in cleartext. `parsePeer()` believes whatever string it is given. On the Remote, `RemoteSession.startPairing()` and `beginReconnect()` set `autoAccept = { peer.installId == prefs.lastPeerId }`. `RemotePairingScreen` (l.93-98) **auto-connects** with no tap to the first discovered Camera whose `installId == lastPeerId`. `_discovered` dedups by `installId` and keeps the newest (`onEndpointFound`), so the real Camera can be shadowed.

**Exploit.**
1. Mallory sits within Bluetooth range of Alice (Camera) and Bob (Remote) and reads Alice's advertisement: `C|3f9a12cd|Alice's Pixel`.
2. Mallory runs a modified build that advertises the same string.
3. Bob's link blips, or Bob simply opens the Remote again later, since `lastPeerId` is kept forever. Bob's phone discovers Mallory's endpoint (the newest one replaces Alice's in the list), connects on its own, and **auto-accepts with no code**. Bob sees no prompt.
4. Mallory now controls the "Camera" Bob sees. Mallory can show fake preview frames, push arbitrary images into Bob's gallery (S5), show arbitrary text via `ShutterRejected`/`SessionEnded` reasons, and read **Bob's `installId`** from his connection request.
5. Chain: with Bob's `installId`, Mallory impersonates Bob to Alice's Camera (S2).

**Fix.**
- Replace `installId`-based trust with a **pairing secret**. After the first code-confirmed Allow, both sides generate and store a random 32-byte key per peer (`Prefs`, keyed by peer id). On any auto-accepted connection, run a challenge-response HMAC as the first message exchange (a new `Cmd.Auth`/`AuthReply`). Until it succeeds, drop every other command and frame in `NearbyLink.payloads`, and disconnect if it fails or takes more than 3 s.
- Stop advertising the stable `installId`. Advertise a per-session random token instead (see S12). For reconnects, recognize the peer by a token derived from the pairing key and a rotating nonce.
- Make the pairing-screen reconnect **one tap** (a highlighted "Reconnect to Alice's Pixel" button), not automatic, as the README already says ("one-tap").

### S2: Unbounded silent re-entry for the "session Remote" (High)
**Code.** `CameraSession.start()` sets `autoAccept = role == Remote && (!safeMode || peer.installId == sessionRemoteId)`. `sessionRemoteId` is cleared **only** in `endSession()` and `stop()`, not on `Connection.None`. `checkIdle()` doesn't run while disconnected, so the reconnect window lasts as long as the Camera screen stays open. On re-entry the chime is skipped (`if (sessionRemoteId != c.peer.installId …) chime`).

**Exploit.** Bob walks out of range, and Alice leaves the Camera running on the rock ("Waiting for Remote"). Hours later, Bob comes back or Mallory arrives with Bob's `installId` from S1, or from Bob's phone having once advertised as a Camera, because `installId` is shared across roles. The Camera accepts silently, makes **no sound**, and the badge reads "LIVE · Bob's Pixel".

**Fix.** In the `Connection.None` branch of `CameraSession.start()`, start a 30 s timer (matching the Remote's `beginReconnect` window) that clears `sessionRemoteId` when it expires. Always chime on reconnect in safe mode, perhaps with a softer tone. Require the S1 challenge-response before re-entry.

### S3: Safe mode off means anyone nearby is auto-accepted, silently and covertly (High)
**Code.** With `safeMode == false`, `autoAccept` returns `true` for **every** peer with role Remote, including peers whose name fails `parsePeer()`, because the fallback `Peer(..., Role.Remote, "", ...)` also counts. No chime plays on connect or end (`CameraSession` l.132, l.205). The LIVE badge is hidden once dimmed (`connected && (safeMode || !dimmed)`), and the dim scrim is 94 % black at 1 % backlight. The idle timeout can be set to "Off". Turning all of this off takes a single toggle in Settings (`SettingsScreen` l.87) with no confirmation, no PIN, and no warning on the Camera screen.

**Exploit.**
1. *Stranger:* Alice turns safe mode off to skip the Allow tap and props the Camera up. Mallory, 20 m away, taps Alice's Camera in the Remote list and is connected **without any prompt or sound**. Mallory can watch live, take photos (which are saved to Alice's gallery and copied to Mallory), switch to the front lens, and delete photos.
2. *Creep:* the owner turns safe mode off, sets auto-disconnect to Off, and hides the Camera phone in a changing room. Ten seconds after the last touch the screen looks switched off and shows no LIVE sign, while live 640 px, 20 fps video streams out indefinitely.

**Fix.**
- Keep the **bystander-facing** guardrails out of the toggle: the LIVE badge always visible, the connect/end chime, and a maximum session length. Only owner-convenience options should be switchable.
- Never auto-accept an *unknown* Remote, whatever the safe-mode setting. At most, allow auto-accept of previously paired, cryptographically verified Remotes (S1).
- Require confirmation to disable safe mode (a dialog that explains the consequences, plus device credential through `BiometricPrompt` with `DEVICE_CREDENTIAL`). Show a persistent "Safe mode OFF" pill on the Camera screen, and surface `status.safeMode` on the Remote (it is sent but never displayed).

### S4: Photos and delete rights leak across sessions to a different Remote (High)
**Code.** `CameraSession` is an app-scoped singleton (`PoseApp.cameraSession by lazy`). `pendingPhotos` and `shots` are never cleared in `stop()` or `endSession()`. `flushPhotos()` runs on **every** `Connected` event, whoever the peer is. `shots` maps ids to gallery URIs across sessions.

**Exploit.** Bob's Remote triggers a 10 s timer shot of Alice's family and then leaves range, or Alice ends the session before the 2 MP copy goes out. The capture still completes, by design, and the copy is queued. Later, in the same app process, Carol pairs her Remote to Alice's Camera, and **Bob's photo is sent to Carol** and saved to her public gallery. Carol also gets its id, so `Delete(id)` from Carol removes Alice's original.

**Fix.** Tag each queued photo and each `shots` entry with the Remote's verified identity (`sessionRemoteId`). In `flushPhotos()`, send only entries whose owner matches the connected peer. Clear `pendingPhotos` and `shots` in `endSession()` and `stop()`, or keep them owner-scoped with a TTL. In the `Delete` handler, accept only ids owned by the current peer.

### S5: Unsolicited image injection and flooding on the Remote (Medium)
**Code.** `RemoteSession.start()` saves every incoming stream straight to `Pictures/Hold That Pose` **before** checking that it decodes (`saveToGallery` at l.170, `decodeThumb` at l.171). Nothing checks that a `Captured(id)` or shutter preceded it. There is no count, size or rate limit, and no `hasSpace()` check. Each shot keeps a 1600 px `Bitmap` in the unbounded `_shots` list (about 7.7 MB each), which is also not cleared in `stop()`.

**Exploit.** A malicious or spoofed Camera (S1) pushes explicit or harassing images, or thousands of files (including non-JPEG bytes named `.jpg`), into the Remote owner's public gallery. There they sync to Google Photos and appear in other apps. After about 50 shots the Remote runs out of memory, and even a long legitimate session hits the same limit.

**Fix.** Accept a photo only if its `header.id` matches an outstanding `Captured` id from this session, and allow only one photo per id. Validate the bytes with `decodeThumb` or a bounds decode, and require JPEG magic bytes, before saving. Cap the byte size (for example 8 MB) and the dimensions. Check `hasSpace()`. Keep only the most recent N thumbnails in memory, or store URIs and decode lazily. Optionally, keep received photos in app-private storage until the user taps Keep, so nothing reaches the public gallery unreviewed.

### S6: Unbounded photo-stream read, a memory DoS on both phones (Medium)
**Code.** `Protocol.readPhotoStream` caps the header at 64 000 bytes, but then calls `data.readBytes()` with **no upper bound**. `NearbyLink.payloads` handles STREAM payloads the same way on both roles, launching one IO coroutine per stream with no concurrency limit. The Camera never consumes `photos`, but it still reads each stream fully into memory.

**Exploit.** A connected (or S1/S2-spoofed) peer sends several multi-GB STREAM payloads. Each coroutine grows a `ByteArrayOutputStream` until the heap is exhausted. `runCatching` swallows the `OutOfMemoryError` in that coroutine, but concurrent allocations in the camera, UI and analysis threads fail and the app crashes. That kills the Camera mid-session and loses the queued `pendingPhotos`.

**Fix.** In `readPhotoStream`, read at most `MAX_PHOTO_BYTES` (for example 8 MB) and fail if more arrives. Better, read the declared length from the header and `readFully` exactly that many bytes. In `NearbyLink.payloads`, reject STREAM payloads unless the local role is Remote and a photo is expected, and cancel them with `client.cancelPayload`. Allow only one stream in flight.

### S7: Auto-disconnect is easily defeated, and there is no hard session cap (Medium)
**Code.** `onCommand` refreshes `lastActivity` for every command except `Ping`, including `KeepAlive`, `Hello`, `Focus`, and even Camera→Remote types sent back (`Status`, `Pong` fall into `else`). The stock Remote sends `KeepAlive` on **any touch** (`userActive`, throttled to 5 s). A running countdown also counts as activity (`checkIdle` l.181), and `Shutter.timer` is unbounded (`optInt`). `timer = 2_000_000_000` gives a countdown of roughly 63 years that keeps the session alive, keeps the screen at full brightness and disables dimming. The Camera phone has **no cancel-countdown control**, so the owner can only leave the screen.

**Exploit.** A creep with a stock Remote taps the screen once a minute and can watch indefinitely. The 60 s default only catches sessions someone forgot about, not deliberate viewing. A modified Remote just sends `KeepAlive` every 50 s.

**Fix.**
- Add an **absolute session limit** in safe mode (for example 15 to 30 min). After it, show a Camera-side "Still OK? Continue" prompt; if nobody answers within 30 s, end the session. The Remote cannot extend it.
- Count only user-intent commands (`Shutter`, `SetLens`, `Focus`, `CancelCountdown`, and throttled `KeepAlive`), and ignore unknown or reverse-direction types.
- Clamp `timer` to the values the UI offers (`0, 3, 5, 10`) in `onCommand`.
- Add a "Stop" or "Cancel" control on the Camera screen, including on the lock overlay.

### S8: Covert live video through Remote screen recording or screenshots (Medium)
**Code.** Still photos come with an audible countdown and shutter sound on the Camera, but the live preview is a silent video stream of about 640 px at up to 20 fps. `RemoteScreen` never sets `WindowManager.LayoutParams.FLAG_SECURE`.

**Exploit.** The Remote holder starts Android's screen recorder, or any recording app, and captures continuous video of the subject. There is no shutter sound, no saved photo on the Camera, and nothing for the person being filmed to notice. The "audible cues" guardrail is bypassed entirely.

**Fix.** Set `FLAG_SECURE` on the Remote window in `RemoteScreen`'s `DisposableEffect`, and clear it on dispose. Consider also setting it on the Camera window. Document that the Remote cannot record video.

### S9: The LIVE badge is barely visible, and overlays or tapjacking are possible (Medium)
**Code.** When the screen is dimmed, `setBrightness(0.01f)` runs and an 86 % black scrim is drawn. The badge sits above the scrim but at **1 % panel brightness**, so in daylight the phone looks switched off from more than about 1 m away. The Camera activity doesn't call `Window.setHideOverlayWindows(true)` (API 31+), and the Allow button doesn't filter touches that arrive while the window is obscured.

**Exploit.**
- (a) In safe mode, bystanders see what looks like a black, off phone on a shelf.
- (b) A companion app with `SYSTEM_ALERT_WINDOW`, installed on the Camera phone by the creep, draws a full-screen black or fake-lock-screen overlay. The activity stays in the foreground, so capture and streaming continue and the LIVE badge is covered.
- (c) An overlay tricks the owner into tapping **Allow**.

**Fix.** While connected in safe mode, keep brightness at 0.15 or higher, or pulse the LIVE badge to full brightness every few seconds. Render the badge larger, with the elapsed session time. Call `activity.window.setHideOverlayWindows(true)` in `CameraScreen` (declare `android.permission.HIDE_OVERLAY_WINDOWS`). For API < 31, set `filterTouchesWhenObscured` on the root view, or ignore touches with `FLAG_WINDOW_IS_(PARTIALLY_)OBSCURED` on the Allow button.

### S10: Audible guardrails can be muted, and reconnects are silent (Medium)
**Code.** `Beeper` uses `ToneGenerator(AudioManager.STREAM_ALARM, 100)`. The 100 is relative to the user's alarm volume, and Do Not Disturb "Total silence" mutes alarms. `chime()` isn't played on a same-`installId` reconnect (S2) and isn't played at all with safe mode off (S3).

**Exploit.** The creep lowers the alarm volume to its minimum (0 on many OEMs) or enables DND. The countdown, shutter and connect chimes all become inaudible, despite the README's "can't be muted".

**Fix.** Before each connect, check the alarm volume with `AudioManager.getStreamVolume(STREAM_ALARM)`. If it is below a threshold, or DND blocks alarms, refuse to start the Camera in safe mode ("Turn up alarm volume to use safe mode"), or raise the level for the tone. Also play the chime on reconnect. Correct the README claim.

### S11: Connection-request spam and pending overwrite (Medium)
**Code.** `NearbyLink.onConnectionInitiated` unconditionally sets `_connection = Pending(newPeer)`. The Camera keeps advertising while Pending (`stopAdvertising` runs only on Connected), so a second request replaces the first, and **Allow** (`acceptPending`) accepts whichever peer is shown. The name on the prompt is the peer-chosen `deviceName`. Nothing rate-limits, blocks or remembers declined peers.

**Exploit.** Alice's Remote requests a connection. Mallory, who is watching the advertisements, sends a request right after, with his phone renamed "Alice's Galaxy". The Camera card now shows Mallory's name and code. If Alice doesn't compare the codes digit by digit, she taps Allow and Mallory is connected. Alternatively, Mallory sends requests in a loop, the Camera keeps showing prompts (prompt fatigue or DoS), and a mis-tap lets him in.

**Fix.** Ignore, or reject with `rejectConnection`, any `onConnectionInitiated` that arrives while a Pending or Connected state exists. Stop advertising as soon as a request is initiated, and restart it after a decline. Keep a per-`endpointId`/token decline counter with a cooldown (for example 3 declines → ignore for 10 min), and offer a "Block" action. On the Allow card, show "New phone, never paired" when the peer is unknown, and make the code comparison more prominent (large digits, a "Codes match" confirm).

### S12: Persistent identifier and PII broadcast over BLE (Low)
**Code.** `deviceName` comes from `Settings.Global "device_name"` (for example "Alice Smith's S24"). The advertisement includes a stable `installId` and is broadcast continuously while the Camera waits.

**Exploit.** Anyone running a Nearby discovery app near a waiting Camera can collect the owner's name and a stable identifier, and can track that phone across places and days.

**Fix.** Advertise a per-session random token and a short user-chosen nickname, defaulting to "Camera" plus two random words. Stop advertising after N minutes of waiting (it can be resumed on tap).

### S13: The Remote can destroy the Camera owner's originals (Low)
**Code.** The `Delete` handler deletes the full-res gallery file with no Camera-side prompt, even while the Camera is paused or backgrounded, because the link and its collectors keep running. The Camera, meanwhile, cannot delete the Remote's copy.

**Fix.** Add a Camera setting, "Let the Remote delete photos here" (default off for unknown Remotes). Otherwise move deleted files to an app-owned trash with a 24 h restore option, or `MediaStore.createTrashRequest` on API 30+. Show a toast on the Camera.

### S14: Command flood, shutter spam and lens thrash (Low)
**Code.** Nothing limits the rate of `Shutter` (each burst runs 3 full-res captures) or `SetLens` (each front/back switch triggers a full CameraX `bind` with up to 4 `bindToLifecycle` attempts on the main thread). `hasSpace` stops the shutter only when less than 60 MB is left.

**Exploit.** A Remote sends `Shutter(0)` repeatedly and fills the Camera gallery with hundreds of photos until 60 MB remain, draining the battery. Alternating `SetLens(front)`/`SetLens(main)` jams the UI and can put the camera HAL into an error state.

**Fix.** Rate-limit in `CameraSession.onCommand`: at most 1 `SetLens` per 1 s, 10 `Focus` per s, and, in safe mode, a per-session photo cap (for example 200) with a minimum 1 s gap between shutter commands.

### S15: Input-validation gaps (Low)
- `Shutter.timer` is unbounded and can be negative. A negative value skips the countdown, which is harmless today because 0 is allowed, but a huge value causes S7. Clamp it to the allowed set.
- `Focus` with missing `x`/`y` produces `NaN`. `Float.coerceIn` passes `NaN` through, and `uprightToBuffer` and `createPoint` run **outside** `runCatching` in `CameraController.focusUpright`. Reject non-finite values.
- The `ShutterRejected.reason` and `SessionEnded.reason` strings sent by the Camera are displayed verbatim on the Remote, so a spoofed Camera can show text like "Enter your PIN…". Map reasons to local enum codes.
- Preview frames are decoded with no dimension check (`RemoteSession` l.150). A 32 KB JPEG can declare very large dimensions and cause allocation spikes. Use `inJustDecodeBounds` and reject images above 1280 px on the long side.
- The command `SharedFlow` uses `tryEmit` with a buffer of 64, so a flood silently drops legitimate `Shutter`/`CancelCountdown` commands. Rate-limiting (S14) addresses this.

### S16: Misleading foreground notification (Low)
The Camera notification always says "Camera is live — waiting for your Remote", even while a Remote is viewing. It uses `IMPORTANCE_LOW` and has no Stop action. **Fix:** update the notification on connect ("Viewed by Bob's Pixel · 3 min") and add a "Stop session" action that calls `cameraSession.endSession()`.

### S17: Backup and transfer (Low)
`allowBackup="false"` is set, which is good, but on Android 12+ it does **not** block device-to-device transfer. `Prefs` (`installId`, `lastPeerId`) could be cloned to a new phone, which then inherits trusted-peer status (S1/S2). **Fix:** add `android:dataExtractionRules` that excludes `sharedpref/holdthatpose.xml` from both `cloud-backup` and `device-transfer`. The pairing keys from S1 should be kept in Android Keystore-wrapped storage.

### S18: Release signed with the debug key (Low)
`release { signingConfig = signingConfigs.getByName("debug") }`. The README tells people to sideload CI APKs, so users can't tell a legitimate build from a tampered one signed with another debug key, and updates break whenever CI's debug key changes. **Fix:** use a real upload key held in CI secrets, and publish the certificate fingerprint.

---

## Checked and OK (no finding)
- **Exported components:** only the launcher `MainActivity`. `SessionService` has `exported="false"`, and its `PendingIntent` is `FLAG_IMMUTABLE`.
- **Settings can't change mid-session:** Settings is reachable only from Home, and navigating to Home calls `cameraSession.stop()`. The Camera screen's cached `safeMode` therefore can't go stale.
- **No capture in the background:** `CameraController` binds to the activity lifecycle, so CameraX closes on `ON_STOP`, and `takePicture` checks `paused`. No `showWhenLocked`/`turnScreenOn` is set, and the lock overlay is in-app only.
- **The Remote can't loosen the timeout:** `stricterTimeout` ignores values ≤ 0 or larger ones, and safe mode forces a nonzero timeout via `Prefs.effectiveTimeout`. (The issue in S7 is a different one.)
- **Ping doesn't count as activity**, and `SessionEnded` stops the Remote from auto-retrying.
- **Command JSON size** is bounded by the Nearby BYTES limit (32 KB), and the photo header by 64 000 bytes. JSON parse errors are caught (`runCatching`).
- **No path traversal:** gallery file names come from `PhotoStore.fileName(takenAt)`. The peer-supplied `id` is never used in a path.
- **EXIF:** review copies are re-encoded through `Bitmap.compress`, which strips metadata. CameraX doesn't add GPS unless asked.
- **Logging:** only `Log.w` with exceptions. No photos, ids or names are logged.
- **Idle-timeout plumbing:** focus coordinates are clamped (except for NaN, see S15), and the `Hello` name isn't used for display.

## Missing capabilities that would materially improve safety
1. **Cryptographic pairing:** a per-pair secret established after code confirmation, challenge-response on every reconnect, and ephemeral advertising tokens (fixes S1, S2, S12, S17).
2. **A hard session cap with Camera-side re-consent**, plus an elapsed-time counter on the LIVE badge (S7).
3. **Session log on the Camera:** who connected, when, for how long, and how many photos were taken or deleted. Viewable in Settings and not deletable from the Remote.
4. **Kick, block and forget:** a Camera-side "Disconnect and block this Remote", a "Forget paired phones" option on both sides, and cooldowns on declined requests (S11).
5. **Guardrails the owner can't turn off:** separate bystander protections (LIVE, chime, cap, no silent stranger accept) from owner conveniences, and protect "Safe mode off" with device credentials (S3).
6. **Camera-owner control of data flow:** toggles for "Send copies to the Remote" and "Remote may delete here", plus quarantine-until-Keep on the Remote (S4, S5, S13).
7. **A protocol hardening layer** in `NearbyLink`: role-aware filtering (the Camera accepts only Remote→Camera commands, the Remote only Camera→Remote), per-type rate limits, size caps, schema and range validation, and a protocol version field in `Hello` (S6, S14, S15).
8. **Anti-recording and anti-overlay:** `FLAG_SECURE` on the Remote and `setHideOverlayWindows` on the Camera (S8, S9).
9. **An audibility check:** refuse to start safe mode if the alarm stream is muted or DND blocks it (S10).
