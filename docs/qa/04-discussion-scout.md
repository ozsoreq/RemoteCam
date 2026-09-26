# QA 04 — Discussion round: Scout's response

Scout (product/UX) responding to Sentinel (`01`, S-ids) and Breaker (`02`, R-ids), with my own `03` (P-ids).
I re-checked the claims I respond to against the code:
- `AppRoot` has the only `BackHandler`, so R4 holds.
- The manifest `configChanges` lacks `fontScale`/`density`/`locale`, so R5 holds.
- `Cmd.Deleted` is sent but the Remote ignores it, so R14 holds.
- `RemotePairingScreen` auto-connects to `lastPeerId` without a tap, so S1 and R6 hold.

## 1. Duplicates / merges

| Merged ID | Title | Merges |
|---|---|---|
| **M1** | Safe-mode-off is a covert mode: keep bystander guardrails always on, confirm the switch, show the state on both phones | P1, P2, S3, R26 (and P13's "Remote never sees `status.safeMode`") |
| **M2** | Audible and visible cues can be muted or dimmed away (alarm stream, silent reconnect, capture blink under the dim veil, 1 % backlight) | P7, S10, S9 (brightness part) |
| **M3** | Delete: one tap, no undo, deletes the other owner's original, and the ack is ignored | P5, S13, R14 |
| **M4** | Auto-disconnect is dodgeable and there is no session cap; also clamp `timer` and count only intent commands | P6, S7, S15 (timer part) |
| **M5** | Queued photos and shot ids leak to the next Remote | S4, R1 |
| **M6** | Photo transfer framing: no length/CRC/size cap, dropped before delivery, truncated JPEGs saved | S6, R2 (plus the validation part of S5) |
| **M7** | Remote photo intake: unsolicited saves and unbounded 8 MB bitmaps causing OOM | S5, R3, R10 (null-decode path) |
| **M8** | Silent trust of `installId`: zero-tap auto-connect, unbounded silent re-entry, decline loop | S1, S2, R6, R7 (approval-gate part) |
| **M9** | Environment/link-start failures: no radio/location/Play-services check, no retry, wrong message | P11, R12 |
| **M10** | Honest foreground notification plus a one-tap Stop on the Camera | S16, P17, P16 (item 5) |
| **M11** | Broadcast identity: real owner name and a stable id, not editable | P14, S12 |
| **M12** | Leaving the Camera: Back works while locked, no confirmation mid-capture, yet a bystander has no quick Stop | R16, P17 (see §2 for how to reconcile) |
| **M13** | Configuration change (font size, split-screen) orphans the session; hits large-font users mid-session | R5, P8 (large-font users are the trigger) |

## 2. Agree / challenge (user-facing behaviour I want)

**Sentinel**
- **S1 (installId trust): agree on the problem, refine the fix.** The pairing screen should show a highlighted **"Reconnect to Alice's Pixel"** card and connect on one tap, instead of auto-connecting. That is still "one-tap pairing" as the spec says.
  - Keep the fully **automatic** reconnect only inside the 30 s drop window (`beginReconnect`). A solo traveller mid-shoot must not have to walk back to the rock.
  - The per-pair HMAC secret should be invisible to users: no extra screens, and the handshake failure message is simply "Couldn't verify — pair again".
  - Rotating advertising tokens (S12) change reconnect semantics and can't be tested without two devices, so they are LATER.
- **S2: agree.** A 30 s silent re-entry window matches the Remote's 30 s reconnect window, so nothing changes for honest users. Play a *soft* reconnect chime, not the full connect chime, so a flaky link isn't noisy.
- **S3: agree, with one change.** Protect "turn safe mode off" with a **hold-to-confirm sheet** listing what is lost, not a mandatory `BiometricPrompt`. That avoids a new dependency and an emulator-untestable path. The device-credential gate is SHOULD, via `KeyguardManager.createConfirmDeviceCredentialIntent`, only when a lock screen exists.
- **S7 (session cap): agree, with a longer cap for honest users.** Use **30 min** (not 15). At the cap, the Camera chimes, the LIVE badge goes full brightness, and the Remote shows "The Camera asks to continue: tap Continue on the Camera within 60 s". A golden-hour shoot then costs one walk to the rock every 30 min, which is acceptable. Clamping `timer` to {0, 3, 5, 10} is a free MUST.
- **S8 (FLAG_SECURE on the Remote): agree, but scope it.** Set it **only while the live viewfinder is visible**, not in Review, so users can still screenshot or share a photo they took. Two costs to note:
  - The CI screenshot pipeline will render the Remote live screen black, so exempt debug builds or accept that.
  - It doesn't stop someone filming the Remote with a third phone. Treat it as friction, not a guarantee.
- **S9: agree on `setHideOverlayWindows` (cheap).** For brightness, keep a floor of 0.12 while connected in safe mode, rather than pulsing window brightness, which is jarring and looks like a bug. Make the LIVE badge larger and add elapsed time ("LIVE · 3:12").
- **S10: disagree with "refuse to start".** Refusing blocks an honest user whose alarm volume happens to be low. Instead, **raise the alarm stream to a floor for the session and restore it on exit**. If DND blocks it (`setStreamVolume` can throw `SecurityException`), show a persistent "Muted: bystanders won't hear the countdown" notice on both phones. Also correct the README claim.
- **S11: agree.** Ignore a second request while one is Pending: cheap and a MUST. On the Allow card, "New phone, never paired" is a good UX cue.
- **S13: agree, but my default is gentler.** Deleting on the Remote deletes **locally with 5 s Undo**. The Camera copy is deleted only if the Camera owner allowed it, via a Camera setting that is off by default for Remotes not paired before. The Review caption should then say what actually happens ("Deleted here · Camera keeps its copy").
- **S14, S15, S17: agree.** They are cheap and unit-testable.
- **S18: agree, but it's a release/secrets task, not app code.** LATER.

**Breaker**
- **R4 (Back in Review disconnects): strong agree.** I missed this. It is the top UX bug for anyone who uses gesture navigation.
- **R5: agree.** Add `fontScale|density|smallestScreenSize|locale|layoutDirection` to `configChanges` and make `screen` saveable. This matters for my accessibility persona: turning up font size mid-shoot currently drops the LIVE badge.
- **R6 (decline loop): strong agree.** From the Camera owner's side this is prompt spam, and it overlaps S11. After a decline, show "Camera declined" on the Remote and don't auto-connect again that visit.
- **R7: agree.** "Tap Allow on the Camera" must show on the Remote whenever the Camera is waiting for approval. Otherwise users watch "Reconnecting… 12s" run out with no idea why.
- **R8: I'd reject with a message rather than queue.** Queuing a hidden second shot surprises users. Send `ShutterRejected("Still saving the last photo")` and show the shutter as busy while `awaiting`. It's simpler and testable.
- **R15 (dim when unconnected): refine.** Don't dim while the pairing code or Allow card is on screen. The user is walking between phones comparing digits. Dim after 60 s when unconnected and not Pending. Unbind the camera and stop advertising after 10 min in the Ended state.
- **R16 vs my P17: reconcile.** Back while locked should do nothing (agree). **X during a countdown or capture** waits for the photo to finish before closing, or asks "Photo in progress, leave anyway?". But the **Stop** chip next to LIVE (P17, S16) stays **one tap and works while locked**. Ending a session is the safe direction; only *changing* settings needs friction.
- **R17 (per-frame allocations): agree on the diagnosis, LATER.** Nothing can be measured without a device, and it risks regressions in the preview.
- **R22: agree.** Disable the `LensPicker` while counting. It's the cheapest fix and shows the user why.
- **R24: agree.** Volume keys should change the volume when there's no live session and in Review.

## 3. Votes

Constraint: one engineer, one pass, CI only (compile, JVM tests, single-phone emulator). MUST = safety, privacy, crash or data loss that can be fixed in a self-contained, testable way. SHOULD = cheap wins. LATER = needs a device, a new subsystem, or non-code work.

### MUST (implement now)
| Item | Minimal scope |
|---|---|
| M1 (P1, P2, S3, R26) | LIVE badge always shown, chime always on, never auto-accept an unknown Remote, max idle limit even when safe mode is off; hold-to-confirm sheet; "Safe mode off" chip on the Camera and the Remote (from `status.safeMode`) |
| M2 (P7, S10) | Raise the alarm stream to a floor for the session and restore it after; "Muted" notice if that isn't possible; move the capture blink above the dim veil; soft chime on reconnect |
| M3 (P5, R14, S13) | 5 s Undo before any delete; the Remote deletes locally only unless the Camera allows it; `Deleted(id, ok)` handled with a notice |
| M4 (S7 intent-only activity, S15 timer clamp) | Clamp `timer` to {0, 3, 5, 10}; only user-intent commands refresh activity |
| M5 (S4, R1) | Tag queue entries and `shots` with the Remote id; flush and delete only for the owner; clear them on `endSession` and when a new Remote is allowed |
| M6 (S6, R2) | `len` + CRC in `PhotoHeader`; capped `readFully`; reject STREAM on the Camera role; never save a truncated file (unit-testable in `ProtocolTest`) |
| M7 (S5, R3, R10) | Keep URI + ≤256 px thumb and decode on demand; accept only an expected id; validate before saving; clear `awaiting` on every failure path plus a 20 s timeout |
| M8 (S1 minimum, S2, R6) | One-tap "Reconnect" card instead of silent auto-connect; clear `sessionRemoteId` 30 s after a drop; stop auto-connecting after a decline |
| R4 | `BackHandler` closes Review |
| R5 | Extra `configChanges` plus saveable screen derived from the live session |
| R11 | Timeout on `takePicture`/capture; always reset `_saving` |
| R13 | Don't report "Saved" or delete the burst when the gallery save failed |
| S11 | Ignore or reject a second request while one is Pending |
| P3 | Settings "About": version, privacy-policy link (URL in `strings.xml`; the owner must supply the real one), a short offline privacy summary, OFL licences |
| P8 (minimum) | Font-scale-proof `CodeDigits`; `verticalScroll` on Home, Permissions and the Camera `WaitingCard` body |

### SHOULD (implement now if cheap)
- **Sentinel:**
  - S7 30-min session cap with Camera-side Continue.
  - S8 `FLAG_SECURE` on the live viewfinder only.
  - S9 `setHideOverlayWindows`, a 0.12 brightness floor, and elapsed time on LIVE.
  - S14 rate limits.
  - S15 remaining validation: NaN focus, reason codes, frame bounds.
  - S17 `dataExtractionRules`.
  - S3 device-credential gate.
- **M10 (S16, P17):** live notification text, a "Stop session" action, and a Stop chip next to LIVE.
- **Breaker:**
  - R7: "Tap Allow on the Camera" state, plus retry within the window.
  - R8: reject with a message.
  - R9, R16 (Back disabled when locked; X defers during capture), R19, R20, R21, R22.
  - R15: dim after 60 s unconnected (not while Pending); release the camera after 10 min Ended.
  - R12/P11 (M9): Play-services, BT, location and Wi-Fi check with a fix card and a "Try again" button.
  - R18, R23, R24, R25.
- **Mine:**
  - P4 scoped down to a **Share button in Review** (`ACTION_SEND` with the saved URI).
  - P9: toggle state, `selectable` and `liveRegion` semantics.
  - P10: `minimumInteractiveComponentSize()` in `pressable`.
  - P12: brightness boost on the Remote while Live, and `PaperFaint` ≥ 0.55 for text.
  - The rest of P8: `heightIn` instead of fixed heights; countdown numeral ÷ `fontScale`.
  - P13: show the Camera's timeout and safe-mode state on the Remote (read-only).

### LATER (with reason)
| Item | Reason |
|---|---|
| S1 full per-pair HMAC and rotating tokens, S12/P14/M11 nickname and ephemeral ids | Protocol and trust redesign; needs two-device testing to avoid breaking reconnect |
| S18 release signing | Needs CI secrets and a key-management decision, not app code |
| R2 ack/resend and R1 disk-backed outbox (beyond M5/M6) | New subsystem; the risk of a drop mid-transfer can only be checked on devices |
| R17 per-frame bitmap reuse | Performance work that can't be measured without hardware; risks preview regressions |
| P4 full-res fetch, zoom, recent-photos library | Each is a feature on its own; Share covers the core need now |
| P13 in-session settings sheet on the Camera | Needs a new UI surface plus live re-application of settings |
| P15 localisation / Hebrew / RTL | Large mechanical extraction (every string); do it as its own pass, then enable `supportsRtl` |
| P16 Console items (FGS video, target audience 13+/18+, Data safety) | Store-listing work, not code; do it before submission |
| P18 help/FAQ/feedback | Low impact; reuse the M9 checks once built |
| S13 Camera-side "Remote may delete here" setting beyond the M3 default | Covered by the M3 default (local-only delete) for now |
