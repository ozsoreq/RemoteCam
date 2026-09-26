# QA 04 — Discussion round: Breaker's response

Inputs: 01 (Sentinel S1–S18), 02 (Breaker R1–R26), 03 (Scout P1–P18). I re-checked the
teammate claims marked "verified" below against the code. Constraint: one engineer does one pass,
without a physical device. Checks are CI compile, unit tests (`app/src/test/.../LogicTest.kt`,
`ProtocolTest.kt` already exist) and a single-phone emulator smoke test. So the fixes favoured
here are pure functions and self-contained state changes that JVM tests can cover.

## 1. Duplicates and merges

| Merged ID | Title | Sources |
|---|---|---|
| **M1** | Photo outbox is not tied to a session: it leaks to the next Remote, and ids are deletable across sessions | R1, S4, R26 (leak part) |
| **M2** | Photo transfer has no integrity or size bound: loss or truncation on drop, and a memory DoS | R2, S6, and R10 (stuck "Saving…" is the symptom) |
| **M3** | Received photos on the Remote are unbounded and unvalidated: OOM crash and image injection | R3, S5 (memory and validation parts), P4 (in-memory list lost on restart) |
| **M4** | Deleting on both phones is unsafe: no confirmation or undo, the Camera copy is silently kept, and the Remote can wipe originals | P5, R14, S13 |
| **M5** | Safe mode off is a covert camera: silent auto-accept of anyone, LIVE hidden when dim, no chime, no cap | S3, P1, R26, plus P2 (one-tap disable, no indicator) |
| **M6** | Auto-disconnect is defeatable and there is no session cap; unbounded `timer` | S7, P6, S15 (timer part), R11 (hung capture counts as activity) |
| **M7** | Audible guardrails muted via the alarm stream | S10, P7 |
| **M8** | Pairing and reconnect trust: auto-connect on a spoofable `installId`, unbounded silent re-entry, decline loop | S1, S2, R6, R7 (approval gate) |
| **M9** | Pending-request handling: overwrite, spam, no cancel or timeout | S11, R21, R18 (wrong "declined" message) |
| **M10** | Link start failures (BT, Wi-Fi, Location, Play services) are silent, terminal or mislabelled | R12, P11 |
| **M11** | Broadcast device name and id (PII, tracking, indistinguishable phones) | S12, P14 |
| **M12** | Foreground notification is static and has no Stop action | S16, P16 item 5 |
| **M13** | Leaving or stopping from the Camera: Back works while locked, no Stop for bystanders, exit kills the burst | R16, P17 |
| **M14** | Camera screen visibility: LIVE at 1 % brightness, overlays and tapjacking | S9, and the dim part of P1 |
| **M15** | Command abuse and flood: shutter spam, lens thrash, busy shutter dropped silently | S14, R8, R22 |

Everything not listed is a standalone finding.

## 2. Agree or challenge

**S1 (crypto pairing).** I agree with the threat. For this pass I challenge the scope: HMAC
challenge-response plus key storage plus a protocol change can't be verified without two devices,
and a bug there bricks every reconnect.
- **Minimal, safe version:** remove the zero-tap auto-connect on the *pairing screen*. Replace it with a one-tap "Reconnect to X" row, which also matches the README's "one-tap" wording. This also fixes **R6** (the decline loop).
- **Keep** silent auto-reconnect only *inside* a live session (`beginReconnect`, within its window). That is the reliability-critical path, and there the spoof window is small.
- Crypto pairing is LATER.

**S2 (clear `sessionRemoteId` after 30 s).** I agree, with one refinement: the Camera's window must be
**longer** than the Remote's. The two sides detect a drop at different times, and the Remote retries for 30 s from *its*
detection. Use `REENTRY_WINDOW = 60 s`, started at `Connection.None`. Also play a soft chime on
re-entry. After the window, safe mode needs Allow again, and the Remote must *say so* (R7: show
"Tap Allow on the Camera" when `Pending(accepted=true, code≠"")` during reconnect). Otherwise the stricter
gate turns into a mystery "Lost".

**S3/P1 (bystander guardrails always on).** I agree: always show LIVE, always chime, never auto-accept an
*unknown* Remote. There is no reliability cost. The previous-peer skip should use the in-session
`sessionRemoteId` window (S2), not the persistent `lastPeerId`, which S1 shows is spoofable. I disagree with
P1's "keep 10 min idle max when off" only if M6's cap lands anyway; one mechanism is enough.

**S4 (clear `pendingPhotos`/`shots` on `endSession`/`stop`).** I agree. That is stricter than my R1
("persist across restart"), and I accept it. Refinement: keep entries during a *link drop* (that is the
advertised "photo syncs on reconnect" feature), tag each one with the `sessionRemoteId` at capture, flush only
to the matching peer, and clear on deliberate end, stop, or when the re-entry window (S2) expires. Disk
persistence is LATER.

**S5 (accept a photo only if its id matches an outstanding `Captured`).** **Challenge.** If the link drops
at zero, the Remote never receives `Captured(id)`, so the legitimate synced photo would be rejected, which
breaks the drop-survival feature. Instead:
- M1 guarantees the sender is the in-session Camera;
- the Remote validates each photo: JPEG magic bytes, length equal to the header `len` (M2), size ≤ 8 MB, dimensions ≤ 4096;
- the Remote accepts at most one photo per id, and at most about 1 per second.

That keeps injection low without false rejects. Quarantine-until-Keep is LATER.

**S6 (size cap).** I agree. Merge it with the R2 fix: the header carries `len`, and the reader uses
`readFully(len)` with `len ≤ 8 MB`, throwing otherwise. On the Camera role, reject STREAM payloads outright. This is
pure and unit-testable in `ProtocolTest`.

**S7/P6 (session cap).** I agree with a cap. **Refinement for reliability:**
- Don't count down and end during a countdown or capture. Defer until idle.
- Flush the outbox *before* `SessionEnded`.
- Make it a **soft** cap, safe mode only: at 30 min the Camera chimes, shows LIVE at full brightness and a Camera-side "Continue" button, and the Remote sees the same warning as the idle one. With no Camera tap within 60 s → `endSession("Session limit reached")`.
- The Remote can't extend it.

Put the decision in a pure function, `capState(now, startedAt, busy)`, so it can be unit-tested. Also count
only intent commands (`Shutter`, `CancelCountdown`, `SetLens`, `Focus`, `KeepAlive`) as activity, and
clamp `timer ∈ {0,3,5,10}`. Scout's alternative of a 2 min default is fine either way.

**S8 (`FLAG_SECURE` on the Remote).** I agree; it is one line. Side effect: emulator screenshots of the Remote screen come
out black. That doesn't matter today, because the Remote screen isn't screenshot-verified.

**S9.** I agree with `setHideOverlayWindows(true)` (API 31+) and `filterTouchesWhenObscured` on Allow. For
brightness, a floor of 0.15 while connected **conflicts mildly with R15 (battery)**. The resolution is to pulse to full
brightness for 1 s every 10 s while connected in safe mode, and allow a deep dim only while unconnected.

**S10/P7.** I challenge "raise the alarm volume for the session". `setStreamVolume` can throw
`SecurityException` under DND, and changing user settings is invasive. Minimal version: check the alarm stream volume and
DND at Camera start, then show a Camera notice and send a `Status` flag so the Remote sees "Camera is muted". Wrap
any volume call in `runCatching`. Fix the README claim.

**S11.** I agree, and this is also a reliability fix. While Pending or Connected, `rejectConnection` any new
`onConnectionInitiated`. **Exception:** during the S2 re-entry window, if the *current* Pending is an unknown peer and the new
one is `sessionRemoteId`, prefer the session Remote. Otherwise a stranger's request can block the legitimate reconnect.

**S15.** I agree: clamp `timer`, reject non-finite `Focus`. I verified `optDouble` → NaN reaches
`createPoint` outside `runCatching`, which is a crash. Mapping `reason` strings to codes is LATER.

**S13/P5 (delete).** I agree: show a confirmation (or undo) and make the delete *this phone only* by default, with an explicit "Also delete on
Camera" option. My R14 adds `Deleted(id, ok)` so the Remote can report when the Camera copy was kept.

**P4.** I agree that Share is cheap (`ACTION_SEND` with the `Shot.uri` already saved). Full-res fetch is a new transfer path
that needs two devices, so LATER.

**P12 (Remote brightness 1.0 while Live).** I agree. The battery cost is on the Remote only. The window flag is reset on dispose,
the same way as on the Camera.

**P13.** It is sound, but in-session settings need new plumbing: LATER. Showing `status.safeMode` on the Remote is cheap: SHOULD.

**P16 item 1 (the Remote starts the FGS while discovering).** I agree it is defensible, since pairing is user-started. Keep it for
reliability, because otherwise the Remote's discovery dies when the screen turns off.

## 3. Votes

**MUST** = implement now. **SHOULD** = implement now if cheap. **LATER** = with reason.

| ID | Vote | Minimal scope or reason |
|---|---|---|
| R1/S4 (M1) | **MUST** | Tag queue entries and `shots` with the remote id; flush only on match; clear on end, stop and re-entry expiry. Unit-test the queue as a small class. |
| R2/S6 (M2) | **MUST** | `len` in the header plus a bounded `readFully`, and `Cmd.PhotoAck(id)`. Remove from the queue only on ack; resend on reconnect; the Remote de-dups by id. Test in ProtocolTest. |
| R3 (M3 memory) | **MUST** | Decode at ≤ 1080 px; keep at most 20 bitmaps (older shots keep only the URI); clear on `stop()`; `runCatching` the decodes. |
| S5 (M3 validation) | SHOULD | Magic bytes, len and dimension checks, one photo per id (see challenge). |
| R4 | **MUST** | `BackHandler(reviewing != null)`. One line. |
| R5 | **MUST** | Add the missing `configChanges` plus `rememberSaveable` screen state. Deriving the screen from the live session is SHOULD. |
| R6/S1 (M8) | **MUST** | One-tap reconnect row instead of auto-connect on the pairing screen. Crypto pairing is **LATER** (needs a two-device protocol test). |
| S2 (M8) | **MUST** | 60 s re-entry window on the Camera, plus a soft chime on re-entry. |
| R7 | SHOULD | A retry loop inside the window, and a "Tap Allow on the Camera" veil when the Camera requires it. |
| R8/S14/R22 (M15) | **MUST** (R8) / SHOULD | R8: send `ShutterRejected("Still saving…")`. S14: 1 s shutter gap. R22: defer `SetLens` while busy. |
| R9 | SHOULD | Keep the countdown shown during reconnect and queue `CancelCountdown`. |
| R10 | **MUST** | Timeout on the awaited photo; clear on the decode-null path. |
| R11 | **MUST** | `withTimeoutOrNull` around `takePicture` and around the whole capture. Otherwise the idle guardrail is bypassed. |
| R12/P11 (M10) | SHOULD | A "Try again" action on both link screens, specific status-code messages, a Location/BT check, and a Play services check. |
| R13 | SHOULD | Don't claim "Saved" or delete temp files when the save fails. |
| R14/P5/S13 (M4) | **MUST** (confirm, this-phone default) / SHOULD (`Deleted(id, ok)` plus undo) | |
| R15 | SHOULD | Dim in every state. Releasing the camera when unconnected is LATER (it needs device verification). |
| R16/P17 (M13) | SHOULD | Disable Back while locked, and add a "Stop" chip next to LIVE that stays reachable while locked. |
| R17 | LATER | A perf refactor; it can't be measured without a device. |
| R18 | SHOULD | Suppress the message on a local reject; clear the error on the Camera. |
| R19 | LATER | Needs changes to the advertising format. |
| R20 | SHOULD | Generation counter in `NearbyLink.stopAll`. |
| R21/S11 (M9) | **MUST** (S11 reject-while-pending, with the session-Remote exception) / SHOULD (R21 cancel button, 60 s pending timeout) | |
| R23, R24, R25 | SHOULD | Trivial each. |
| R26/S3/P1 (M5) | **MUST** | LIVE always visible, always chime, never auto-accept an unknown Remote, regardless of safe mode. |
| P2 (M5) | SHOULD | Confirmation dialog, plus a "Safe mode off" chip on the Camera and on the Remote (from `status.safeMode`). |
| S7/P6 (M6) | **MUST** (clamp timer, intent-only activity) / SHOULD (30 min soft cap, pure `capState`) | |
| S15 | **MUST** | NaN and range validation. Reason-code mapping is LATER. |
| S10/P7 (M7) | SHOULD | Detect a muted alarm stream and warn on both phones; fix the README. Forcing the volume is LATER. |
| S8 | SHOULD | `FLAG_SECURE` on the Remote. |
| S9/M14 | SHOULD | `setHideOverlayWindows` and filtering obscured touches on Allow. The LIVE brightness pulse is LATER (it needs tuning on a device). |
| S12/P14 (M11) | SHOULD (default name = model, no owner name) / LATER (editable name, rotating token) | |
| S16/P16.5 (M12) | SHOULD | Update the notification on connect and add a Stop action. |
| S17 | SHOULD | `dataExtractionRules` XML. |
| S18 | LATER | Needs a real key in CI secrets. |
| P3 | SHOULD | About row: version, policy link (placeholder URL from `strings.xml`), licences. |
| P4 | SHOULD (Share) / LATER (full-res fetch, recents strip, zoom) | |
| P8, P9, P10 | SHOULD | `verticalScroll`, `heightIn`, digit scaling; `stateDescription`/`selectable`/`liveRegion`; `minimumInteractiveComponentSize` in `pressable`. All mechanical. |
| P12 | SHOULD | Remote brightness 1.0 while Live; contrast bump for PaperFaint. |
| P13 | SHOULD (show the Camera's safe-mode state and timeout on the Remote) / LATER (in-session settings) | |
| P15 | LATER | Full string extraction and RTL is a large sweep. |
| P16 | LATER | Console and listing tasks, not code (item 5 is handled in M12). |
| P18 | LATER | Content work. |

**Order for the one-pass engineer:**
1. Protocol changes plus tests (M2, S15, timer clamp).
2. CameraSession outbox and session scoping (M1, S2, R8, R11, M6 activity).
3. RemoteSession (M3, R10, R6).
4. UI one-liners (R4, R5, M5, M4 confirm).
5. The SHOULD list, as time allows.
