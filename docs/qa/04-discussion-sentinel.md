# QA 04: Sentinel's response to Breaker (02) and Scout (03)

**Implementation constraints assumed:** one pass by one engineer, no physical device (CI compiles, runs unit tests and one single-phone emulator smoke test). I therefore prefer small, pure-logic fixes that unit tests can cover, and scope large features down.

## 1. Duplicates and proposed merges

| Merged ID | Title | Merges | Severity |
|---|---|---|---|
| **X1** | Safe mode off is a one-tap, invisible, covert, auto-accept-anyone mode | S3, P1, P2, R26 (plus the "badge unreadable when dimmed" half of S9) | High |
| **X2** | Queued photos and delete rights leak to the next Remote | S4, R1 | High |
| **X3** | Photo stream has no length, cap or ack: memory DoS, truncation, loss | S6, R2 | High |
| **X4** | The Remote accepts, saves and holds any photo in memory without limits (injection, OOM, stuck "Saving…") | S5, R3, R10 | High |
| **X5** | Auto-disconnect is easy to bypass, and there is no session cap or capture watchdog | S7, P6, R11 (the idle-bypass part) | Medium-High |
| **X6** | "Unmutable" beeps play on `STREAM_ALARM` | S10, P7 | Medium |
| **X7** | Delete is instant, affects both phones, has no undo, and has no honest acknowledgement | S13, P5, R14 | Medium |
| **X8** | Real device name and stable id are broadcast over BLE, and the name can't be changed | S12, P14 | Low-Medium |
| **X9** | The foreground notification is static and misleading | S16, P16 item 5 | Low |
| **X10** | Pairing prompt spam and overwrite, decline loop, and approvals that never time out | S11, R6, R21, R18 | Medium |
| **X11** | Exiting and stopping from the Camera: Back works while locked, and bystanders have no Stop | R16, P17 | Medium |
| **X12** | The Remote never shows the Camera's safe-mode state | P2 (Remote half), P13 (second half) | (covered by X1) |

S1 and S2 (identity and reconnect trust) have no duplicate, but they interact with R6, R7 and P6. See section 2.

## 2. Agree, challenge or refine

**Conflicts that need a single decision**
- **R7 (retry reconnect more aggressively) vs S1/S2 (gate reconnect).** I agree with retrying *within* the 30 s window, provided every auto-accepted reconnect is verified.
  - Scoped fix for S1/S2: on Allow, the Camera sends a random 128-bit `sessionKey` inside the already code-verified, Nearby-encrypted link (`Cmd.Welcome(key)`, kept in memory on both sides).
  - On any auto-accepted reconnect, both sides exchange nonces and `HMAC-SHA256(key, nonce)` (`javax.crypto.Mac`, about 60 lines, fully unit-testable) before commands or frames are processed. A mismatch or a 3 s timeout disconnects.
  - Identity then no longer rests on the spoofable `installId`, so aggressive retry is safe.
  - The Camera clears `sessionRemoteId` and the key 30 s after `Connection.None`. The window matches the Remote's.
- **P6 (let "Start again" re-admit the same Remote without Allow for 5 min).** I accept this only if the Camera keeps the session key and the Remote passes the HMAC check. Without the check it is S2 all over again, since `installId` is public. SHOULD, and only if the handshake above lands.
- **P1 (keep bystander protections on even when safe mode is off) vs my S3 fix.** We agree. I also want: never auto-accept an *unknown* Remote in any mode (P1 already says this), and remove the `|| !dimmed` branch. I disagree with P1's "a previously approved Remote (`lastPeerId`) may skip Allow". `lastPeerId` is a self-asserted string, so the skip is allowed only after the HMAC check, which this pass keeps in memory only. Across app restarts: Allow is required (persistent pairing keys are LATER).
- **R16 (disable Back while locked) vs P17 (one-tap Stop reachable while locked).** Both are right. Ending a session must stay effortless, because that is the privacy-safe direction. Decision: ignore Back while locked, and add a visible **Stop** chip next to LIVE that works even while locked. Stop and Leave defer `controller.detach()` until `_saving` is false, which satisfies R16's "don't kill the shot".
- **R14 fix (look up the Camera photo in MediaStore by `DISPLAY_NAME = fileName(takenAt)`).** I challenge this. File names are predictable millisecond timestamps, and `takenAt` could be supplied by the peer, so a Remote could delete **any** "Hold That Pose" photo on the Camera, including other people's sessions. Delete only ids that this Remote owns in the current session (X2), and reply `Deleted(id, ok)`. Persisting the id→uri map is LATER.
- **P4.2 (`RequestFull(id)` streams the original).** This is a security-sensitive feature. It must be owner-scoped (X2) and the size cap (X3) must allow it. It is also a large feature, so LATER. P4.1 (a Share button) is fine.
- **R1 (persist the outbox to `filesDir/outbox`).** Persistence is fine because the storage is app-private, but it must be keyed per Remote and expire. The keying and clearing is MUST. Disk persistence is LATER, because it can't be verified without two phones.
- **R2/X3.** Adopting `len` in `PhotoHeader` also fixes my S6 cleanly: `readFully(len)` with `len ≤ 16 MB`, and the Camera role rejects STREAM payloads. MUST. `PhotoAck` and resend are SHOULD. Add a CRC only if it's cheap.
- **P13 (settings during a session).** Allowing changes only while unconnected or ended is fine. Safe mode and the timeout must never loosen mid-connection.
- **P7/X6.** I prefer "block the Camera start plus a notice on the Remote" over silently raising the alarm volume. Changing volume while Do Not Disturb is on needs notification-policy access, and silently restoring it is fragile. Raising the volume is acceptable as a fallback.

**Agreements with notes**
- **R5 (activity recreation).** Agree, High. Security note: the LIVE badge disappears while the link stays connected. Today CameraX unbinds, so nothing streams covertly, but the fix must restore the Camera screen, not just re-bind the camera behind the Home screen.
- **R6.** Agree, and it is security-relevant: a prompt loop invites an accidental Allow (S11). Merged into X10.
- **R11 (capture watchdog).** Agree. It also closes a path that bypasses idle (a stuck `_saving` means the session never ends).
- **R15 (dim or unbind when unconnected; stop advertising after N min).** Agree. This also shrinks the X8 broadcast window.
- **R19** (a "session ended" marker in the endpoint name). Acceptable. It leaks no new information.
- **R26 fix** ("prefer `sessionRemoteId` for 30 s"). Insufficient on its own; superseded by X1 and the HMAC check.
- **P3, P8–P12, P15, P16, P18, R4, R8–R10, R12, R13, R17, R20–R25.** No security objection. These are the other reviewers' calls, and my votes follow.
- **My S8 (`FLAG_SECURE` on the Remote).** Side effect for the team: screenshot tests of the Remote screen will capture black. Nothing screenshots that screen today (it needs a paired Camera). Keep it.

## 3. Votes

**MUST (implement now)**

| Finding | What to implement |
|---|---|
| X1 (S3/P1/P2/R26) | Never auto-accept an unknown Remote. LIVE badge and chime always on. Maximum idle of 10 min even with safe mode off. Confirmation to turn it off. "Safe mode off" chip on the Camera and on the Remote (from `status.safeMode`). |
| X2 (S4/R1) | Tag queue and `shots` entries with the Remote id. Flush and Delete only for the owner. Clear on `endSession`/`stop`. |
| X3 (S6/R2 part) | `len` in the header, `readFully` with a 16 MB cap, and the Camera ignores or cancels STREAM payloads. |
| X4 (S5/R3/R10) | Save only for an outstanding `Captured` id. Validate the decode before saving. `hasSpace` check. Keep small thumbnails plus a bounded list. Clear `_awaitingPhoto` on every failure path, plus a 20 s timeout. |
| X5 (S7/P6/R11) | Clamp `timer` to {0,3,5,10}. Only intent commands reset idle. Hard cap of 30 min in safe mode (then end, or ask "Continue" on the Camera). `withTimeoutOrNull` on `takePicture`. |
| S1 (scoped) | Replace the zero-tap auto-connect in pairing with a one-tap "Reconnect" button, plus the in-memory session-key HMAC on auto-accepted reconnects. |
| S2 | Clear `sessionRemoteId` and the key 30 s after a drop. Chime on reconnect. HMAC gate. |
| S8 | `FLAG_SECURE` on the Remote screen. |
| X10 (S11/R6/R21/R18) | Ignore new initiations while Pending. Suppress auto-connect after a reject. 60 s pending timeout. Correct the decline message. |
| X11 (R16/P17) | Stop chip next to LIVE (works when locked). Back ignored while locked. Defer detach until the save finishes. |
| S15 | Clamp all inbound values, reject NaN, map reason strings to local codes, bound frame dimensions. |
| R4, R5, R13 | Back handler in Review; saveable or derived screen state plus `configChanges`, and reset `_bound`; don't report "Saved" on failure or delete the files. |
| P3 | About row, privacy-policy link and licences (Play requirement, cheap). |

**SHOULD (implement now if cheap)**
- S9 (brightness floor of 0.15 while connected, and `setHideOverlayWindows` on API 31+)
- X6 (S10/P7: volume and DND check with a notice)
- X7 (S13/P5/R14: 5 s undo, "this phone only" by default, `Deleted(id, ok)` shown on the Remote)
- X8 (S12/P14: editable display name, and stop advertising after 10 min unconnected)
- X9 (S16: live notification text and a Stop action)
- S14 (rate limits)
- S17 (`dataExtractionRules`)
- R2's `PhotoAck` with resend
- R7 (retry loop, which needs the HMAC gate first)
- P6 "Start again" re-admit (same gate)
- R8, R9, R12, R15, R22, R23, R24, R25
- P4.1 (Share button)
- P5 (merged into X7)
- P7 (merged into X6)
- P9, P10 (semantics and minimum touch targets, which are mechanical)
- P11 (radio checks with a fix card)
- P12 (Remote brightness, `PaperFaint` contrast)
- P13 (Remote shows the safe-mode and timeout state; settings only while unconnected)
- P16 (manifest and notification parts)
- R20

**LATER**

| Finding | Reason |
|---|---|
| Persistent cryptographic pairing keys and ephemeral advertising ids (full S1/S12) | Needs a key store and a migration, and can't be verified without two phones |
| R1 disk-persisted outbox | Needs two-phone testing |
| R14 persisted id map | See the challenge in section 2 |
| P4.2–P4.4 (full-res fetch, zoom, library) | Features, not fixes |
| P8 (large-font layout sweep) | Broad UI work. Do the `CodeDigits` fix now if trivial, because it hides the security code |
| P15 (i18n/RTL) | Large |
| P18 (help/FAQ) | Not urgent |
| R17 (bitmap reuse) | Perf refactor that needs on-device profiling |
| R19 (ended marker in the endpoint name) | Low |
| S18 (release signing key) | Needs secrets and infrastructure, not code |
| Block list / per-peer cooldown beyond X10 | Nice to have |
