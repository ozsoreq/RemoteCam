# QA activity — conclusions

**Team:** Sentinel (security & abuse), Breaker (reliability & edge flows), Scout (product gaps, UX misuse,
Play compliance, accessibility) → joint discussion → decision record (chaired by Breaker) → Builder
(reviewer + implementer). All findings come from code review; nothing has run on two real phones.

| Stage | Output |
|---|---|
| Round 1: independent review | 62 findings — Sentinel 18 (4 High), Breaker 26 (5 High), Scout 18 (5 High) — `01`–`03` |
| Round 2: cross-review | Duplicates merged, conflicting fixes challenged, every item voted MUST/SHOULD/LATER — `04-*` |
| Round 3: decision | 39 consolidated issues: **28 fix now, 11 later**, with an implementation spec — `05-decisions.md` |
| Build | Builder accepted 19 as specced, changed 9 (safer or more correct), rejected 0; one sub-part deferred — `06-implementation.md` |
| Verification | CI green: build, 35 JVM unit tests, emulator UI tests (Android 14) incl. the new safe-mode confirmation |

## Conclusions

1. **The biggest risk was misuse, not bugs.** Turning safe mode off made the Camera a silent, dark camera that
   accepted any Remote. *Fixed:* Allow for new Remotes, the LIVE sign and chimes are now **always on**;
   turning safe mode off needs confirmation and shows a "Safe mode off" chip; auto-disconnect can no longer be "Off".
2. **Photos could reach the wrong person.** Queued review copies went to whichever Remote connected next.
   *Fixed:* photos are tied to the Remote in session, cleared when it ends, sent with length + CRC32 and
   removed from the queue only after the Remote acknowledges them.
3. **A malicious peer could crash or flood either phone.** No size limits on photo streams; unsolicited
   photos saved to the gallery. *Fixed:* 16 MB cap, exact-length reads, the Camera refuses incoming streams,
   the Remote accepts only photos it asked for and keeps at most 12 small thumbnails in memory.
4. **Pairing trust relied on a public ID.** The broadcast install ID enabled zero-tap reconnects.
   *Fixed (minimal):* no silent connect from pairing — one-tap "Reconnect"; silent re-entry only for the
   same Remote within 45 s of a drop, with a chime; one pending request at a time, 60 s timeout.
   *Later:* a cryptographic pairing key.
5. **Sessions had no real end.** Any touch or a huge timer value kept a session alive forever.
   *Fixed:* only real actions count, timer clamped to 0/3/5/10 s, capture timeouts, and a 30-minute
   "Still OK?" check-in on the Camera in safe mode.
6. **Everyday flows lost state.** Back in Review ended the session; a font-size or split-screen change
   reset the app while still live. *Fixed*, plus delete now offers "here / both" with Undo, a Stop chip beside
   LIVE works even when locked, and busy-shutter taps say why they're ignored.
7. **Compliance gaps closed:** in-app privacy policy link, licences, version, backup exclusions, overlay
   protection, screen-capture blocking on the live view, clearer Bluetooth/Location/Play-services errors,
   accessibility labels and large-font fixes.

## Still open (LATER)
Cryptographic pairing & rotating IDs · editable device name · per-frame bitmap reuse · disk-persisted photo
outbox · full-resolution fetch and zoom · settings during a session · Hebrew/RTL · release signing · Play
Console declarations · help/FAQ · a 48 dp minimum touch size across all controls.

## Before release
- Replace the placeholder `privacy_policy_url` in `strings.xml`.
- **Run the two-phone test**: pairing, Allow, re-entry, photo ack/resend, delete-both, the 30-min check-in
  and the new Nearby error messages can only be confirmed on hardware.
