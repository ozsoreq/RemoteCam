# QA 03 — Product Scout: capability gaps, gameable flows, policy & accessibility

Reviewer: **Scout** (product capability gaps and user-facing flows) · 2026-09-26 · app `0.1.0` (versionCode 1)
Scope: `ui/`, `session/`, `data/Prefs.kt`, `net/NearbyLink.kt`, `media/Beeper.kt`, `AndroidManifest.xml`, screenshots in `docs/screenshots/`.
Walked as: solo traveller, couple, kid, older user, TalkBack / large-font user, bright-sun user, rule-gamer, Play reviewer.
No code was changed.

## Ranked findings

| ID | Title | Area | Severity | Effort |
|---|---|---|---|---|
| P1 | Turning safe mode off turns the Camera into a silent, dark, auto-accepting live camera | `CameraSession.start`, `CameraScreen` | **High** | S |
| P2 | Safe mode goes off with one tap: no confirmation, and nothing on either phone shows it's off | `SettingsScreen`, `CameraScreen`, `RemoteScreen` | **High** | S |
| P3 | No in-app privacy policy, About/version or open-source licences (Play User Data policy) | `SettingsScreen` | **High** | S |
| P4 | Photos can't be shared or fetched at full resolution, and past photos can't be browsed | `ReviewScreen`, `RemoteSession` | **High** | M |
| P5 | One tap on the trash icon deletes a photo on both phones for good: no confirmation, no undo | `ReviewScreen`, `RemoteSession.delete` | **High** | S |
| P6 | Auto-disconnect is easy for a watcher to dodge and harsh on honest users | `RemoteSession.userActive`, `CameraSession.checkIdle` | Medium-High | M |
| P7 | Beeps that "can't be muted" play on the alarm stream, so turning alarm volume to 0 mutes them | `media/Beeper.kt` | Medium-High | S |
| P8 | Large font sizes break key screens: pairing code clipped, Home and Permissions don't scroll | `CodeDigits`, `HomeScreen`, `PermissionScreen`, `CameraScreen` | Medium-High | M |
| P9 | TalkBack can't tell whether toggles are on and doesn't announce status notices | `GlassIconButton`, `LensPicker`, `Chip`, `NoticePill` | Medium | S |
| P10 | Many touch targets are smaller than 48 dp | Remote, Review, Settings, Onboarding | Medium | S |
| P11 | Pairing hangs silently when Location services or Bluetooth are off (older phones) | `RemotePairingScreen`, `NearbyLink` | Medium | S |
| P12 | Hard to read in bright sun: no brightness boost on the Remote, faint text at 3:1 contrast | `RemoteScreen`, `Color.kt` | Medium | S |
| P13 | Settings can't be reached during a session, and the Remote never sees the Camera's safe-mode state | `AppRoot`, `RemoteScreen` | Medium | M |
| P14 | The phone's name is broadcast and can't be changed, and identical phones can't be told apart | `NearbyLink.deviceName` | Low-Medium | S |
| P15 | English-only: strings are hard-coded and RTL is off, so no Hebrew or other languages | whole `ui/`, manifest | Medium (market) | L |
| P16 | Play Console items: FGS declaration, target audience, Data safety, large-screen orientation | manifest / listing | Low-Medium | S |
| P17 | A bystander who spots LIVE has no quick way to stop it | `CameraScreen` lock/dim overlays | Low-Medium | S |
| P18 | No help, troubleshooting or feedback entry point | Home / Settings | Low | S |

Effort: S = under 1 day, M = 1–3 days, L = more than 3 days.

---

## Details

### P1 — Turning safe mode off turns the Camera into a silent, dark, auto-accepting live camera (High)
**Where:** `session/CameraSession.kt` (`start()` autoAccept, lines ~118-120; chimes gated on `prefs.safeMode`), `ui/camera/CameraScreen.kt` (dim overlay 0.94 alpha, LIVE badge condition `connected && (safeMode || !dimmed)`), `data/Prefs.kt` (idle "Off" allowed).
**Persona:** rule-gamer, and every bystander. With safe mode off:
- **No Allow step.** `autoAccept = peer.role == Remote && (!safeMode || …)`, so **any** Remote in range running the app connects without anyone touching the Camera phone. A stranger at the next table can connect to your propped phone, since their Remote only has to tap "Connect".
- **No chime** when someone connects or leaves.
- **LIVE badge hidden** once the screen dims (after 10 s). The screen goes to 1 % brightness under a 94 %-black veil, so the phone looks switched off while it streams 20 fps to the Remote.
- **Auto-disconnect can be "Off"**, so the stream can run indefinitely.
- In practice this is a covert live camera, and it is the pattern a Play reviewer checks under the spyware / surreptitious-capture policy. It also contradicts the README's framing that safe mode "makes intent visible".

**Recommendation:** Split the guardrails into ones that protect **bystanders** and ones that are only **convenience**. Keep the bystander ones on **regardless of safe mode**:
1. The LIVE badge is always visible while connected, including when dimmed (drop the `|| !dimmed` branch).
2. The connect/disconnect chime always plays.
3. A new Remote always needs Allow. Safe mode off may only let a *previously approved* Remote (`prefs.lastPeerId`) skip Allow, never an unknown one.
4. Keep a maximum idle limit (for example 10 min) even with safe mode off.

Safe mode off could then relax only the timeout length and allow a darker standby screen. Update the Settings bullet list to match.

### P2 — Safe mode goes off with one tap: no confirmation, and nothing on either phone shows it's off (High)
**Where:** `ui/settings/SettingsScreen.kt` (the `Toggle` writes `prefs.safeMode` immediately), `CameraScreen` (no indicator), `RemoteScreen` (`CameraStatus.safeMode` is sent over the wire but never shown).
**Persona:** rule-gamer, or a partner who borrowed the phone. Anyone holding the Camera phone can switch safe mode off in about 2 seconds, and **the Camera owner is never told**. The Camera screen looks the same until you notice the missing Allow step or badge. `status.safeMode` reaches the Remote but no UI reads it (grep confirms that `Protocol.kt` is the only user).

**Recommendation:**
- Show a confirmation sheet before turning safe mode off. It should list exactly what is lost and need a deliberate press (hold-to-confirm, or re-accepting the responsible-use text).
- While safe mode is off, show a persistent amber **"Safe mode off"** chip on Home, on the Camera screen (next to LIVE) and on the Remote's link pill (driven by `status.safeMode`).
- Optionally turn safe mode back on automatically after 24 h, or at each app launch.

### P3 — No in-app privacy policy, About/version or open-source licences (High, compliance)
**Where:** `SettingsScreen`. It has Safe mode, Auto-disconnect and Responsible use only. `strings.xml` has no policy URL, and nothing in the UI reads `BuildConfig.VERSION_NAME`.
**Persona:** Play reviewer, and support. The Play User Data policy requires a privacy policy linked in the Console **and inside the app** for apps that use sensitive permissions (CAMERA, nearby devices, location on API ≤ 32). The README flags this itself, but nothing is implemented. Support also can't ask "which version are you on?". The SIL OFL fonts' licence text isn't reachable from the app.

**Recommendation:** Add an **About** row in Settings containing:
- the version name and code;
- a "Privacy policy" link (an HTTPS URL in `strings.xml`, opened with `ACTION_VIEW`) plus a short offline summary: no accounts, no uploads, photos stay on both phones, and the device name is visible to nearby phones (see P14);
- "Open-source licences", covering the two OFL fonts and the Play services / CameraX notices.

Also add the privacy-policy link to the consent screen footer.

### P4 — Photos can't be shared or fetched at full resolution, and past photos can't be browsed (High, capability)
**Where:** `ui/remote/ReviewScreen.kt` (actions are Retake, Keep and Delete only), `RemoteSession.shots` (in memory, lost when the process dies), `CameraSession.capture` (the Remote only ever gets the ~2 MP `reviewCopy`).
**Persona:** couple and solo traveller. Right after the shot, the Remote user wants to post or send it, usually from the phone in hand. Today they have to leave the app, find `Pictures/Hold That Pose` and accept a 2 MP copy, because the full-resolution file sits on the propped phone. After relaunching, the thumbnail and review are empty. There is no pinch-to-zoom to check focus or closed eyes, which is the whole point of reviewing.

**Recommendation (in priority order):**
1. A **Share** button in Review (`ACTION_SEND` with the saved `Shot.uri`).
2. **"Get full-res"** per photo: a new `Cmd.RequestFull(id)` that makes the Camera stream the original file as a STREAM payload. Optionally add an "Always send full-res on Wi-Fi" setting.
3. **Pinch/double-tap zoom** in Review.
4. A **"Recent photos"** strip built from a MediaStore query of the album, so photos survive restarts and are reachable from Home.

This directly addresses the competitor weakness the spec highlights (reliability and speed).

### P5 — One tap on the trash icon deletes a photo on both phones for good: no confirmation, no undo (High)
**Where:** `ReviewScreen` top-right trash (`GlassIconButton(... "Delete on both phones", { current?.let(onDelete) })`), then `RemoteSession.delete` removes it from MediaStore and sends `Cmd.Delete`, and the Camera deletes its full-res original.
**Persona:** kid, older user, couple. A stray tap, or a partner holding the Remote, destroys the **only full-resolution original** on the *other person's* phone. The trash icon is a 40 dp target right next to the status bar.

**Recommendation:**
- Show a 5-second **Undo snackbar**: delay both the local MediaStore delete and `Cmd.Delete` until it expires.
- Default the delete to **this phone only**, with "Also delete on Camera" as an explicit option.
- On Android 11+, consider `MediaStore.createTrashRequest` (trash instead of permanent delete).

### P6 — Auto-disconnect is easy for a watcher to dodge and harsh on honest users (Medium-High)
**Where:** `RemoteSession.userActive()` (any touch on the Remote screen sends KeepAlive at most every 5 s), `CameraSession.checkIdle`, `DEFAULT_IDLE = 60`.
- **Gaming:** someone watching covertly keeps a finger resting on the Remote, or taps now and then, and the session never ends. The idle timer only catches *forgotten* sessions. It never makes a *watched* one visible again.
- **Friction:** a solo traveller pockets the Remote while walking 30 m to the spot. After 60 s the session ends, and the flow is walk back, tap **Start again** on the Camera, tap **Allow** on the Camera, then Connect on the Remote. The 60 s default is short for this.

**Recommendation:**
- In safe mode, add a **maximum session length or periodic re-announce**. For example, every 10 minutes the Camera chimes, flashes LIVE at full brightness, and needs "Continue" on the Camera or a fresh Allow after 30 min.
- For honest users, when **Start again** is tapped the same Remote may rejoin without another Allow for 5 minutes, because tapping Start again is already Camera-side consent.
- Consider a 2 min default.

### P7 — Beeps that "can't be muted" play on the alarm stream, so turning alarm volume to 0 mutes them (Medium-High)
**Where:** `media/Beeper.kt` builds `ToneGenerator(AudioManager.STREAM_ALARM, 100)`. The 100 is relative to the stream volume.
**Persona:** rule-gamer. Setting the Camera phone's alarm volume to 0, which is one slider in system settings, silences every countdown beep, the shutter beep and the connect chime. This undermines the README claim "countdown and shutter beeps can't be muted".

**Recommendation:**
- When a session starts on the Camera, read `AudioManager.getStreamVolume(STREAM_ALARM)`. If it's below a floor, raise it for the session and restore it afterwards (the app has no special permission need for this). Otherwise show a blocking "Turn up alarm volume" notice on the Camera and a "Camera is muted" notice on the Remote.
- Keep the white capture flash visible above the dim veil. The blink `Box` is drawn **under** the dim overlay in `CameraScreen`, so move it above.

### P8 — Large font sizes break key screens: pairing code clipped, Home and Permissions don't scroll (Medium-High, accessibility)
**Where and how:**
- `ui/components/Controls.kt` `CodeDigits`: fixed 60×78 dp tiles hold a 52.5 **sp** digit. At font scale 1.5–2.0 the digit is 79–105 sp and gets **clipped**. That is the security code users must compare, on both the Camera (Allow) and the Remote (CodeSheet).
- `HomeScreen`: the Column isn't scrollable, and a weight(1f) illustration sits above a 64 sp hero plus two role cards. At large scales the Remote card and "Get the app" are pushed off-screen.
- `PermissionScreen`: not scrollable. Three cards plus caption can push the **Allow / Open settings** button off-screen on small phones.
- `CameraScreen` `WaitingCard` (Allow/Decline): not scrollable. The 36 sp title and code tiles overflow the top on small screens.
- `LinkPill` and the Glass buttons have fixed 40 dp heights with text inside, so the text gets clipped.
- The Remote countdown numeral is 220 sp and scales with font size. "10" overflows the viewfinder at 2×.

**Recommendation:**
- Size the code tiles with `widthIn`/`heightIn(min=…)` and wrap the content, or scale the digit style by `1/fontScale` (it is a glyph, not reading text).
- Make Home, Permissions and the WaitingCard body `verticalScroll`, with the primary button pinned outside the scroll area.
- Use `heightIn(min = 40.dp)` instead of `height`.
- Cap decorative numerals with `fontSize = X.sp / fontScale`, the way the onboarding illustration already does.
- Add a screenshot test at `fontScale = 2f`.

### P9 — TalkBack can't tell whether toggles are on and doesn't announce status notices (Medium, accessibility)
**Where:**
- Remote Grid/Level/Mirror/Burst use `GlassIconButton(active = …)` with `role = Button` and no `stateDescription`/`toggleableState`, so TalkBack says "Grid, button" whether the grid is on or off.
- `LensPicker` segments don't say which one is selected. The "0.5×" label is read as "zero point five times" (acceptable) and the selected state is missing.
- Settings `Chip` has no `selected` semantics, and the disabled "Off" chip isn't announced as disabled with a reason.
- `NoticePill` notices ("Camera storage full", "Camera moved", "Disconnecting in 8s") and the countdown aren't `liveRegion`s, so blind users miss them.
- The idle "Keep going" warning appears silently.

Checked OK: the shutter has a dynamic contentDescription, the settings `Toggle` has state, and the Back/Close buttons have labels.
**Recommendation:**
- Use `Modifier.toggleable(value = active, role = Role.Switch)` (or `semantics { stateDescription }`) in `GlassIconButton` when `active` is used.
- Use `selectable(selected, role = Role.RadioButton)` for lens segments and chips.
- Use `semantics { liveRegion = LiveRegionMode.Polite }` on the notices column and the idle warning (Assertive for "Disconnecting").
- Announce each countdown number.

### P10 — Many touch targets are smaller than 48 dp (Medium, accessibility and one-handed use)
**Where:**
- Remote toggles and Disconnect: 40 dp.
- Lens segments: **32 dp high**.
- Review Back and Delete: 40 dp.
- Settings chips: 38 dp.
- Settings `Toggle`: 50×30 dp.
- Onboarding "Skip": text plus 8 dp padding, about 34 dp.

`pressable` uses a bare `clickable`, so Material's `minimumInteractiveComponentSize` isn't applied.
**Persona:** older user, someone wearing gloves, someone using the Remote one-handed at arm's length.
**Recommendation:** Add `.minimumInteractiveComponentSize()` inside `pressable` so the hit area expands and the visuals stay the same. Raise the lens segments to 40 dp or more.

### P11 — Pairing hangs silently when Location services or Bluetooth are off (Medium)
**Where:** `RemotePairingScreen` shows "Finding Camera…" and, after 8 s, "Open the app → Camera on the other phone". `NearbyLink` never checks whether the radios are on (no `LocationManager`/`BluetoothAdapter` use in the code).
**Persona:** older user on Android 8–11. Nearby discovery needs **Location services turned on** below API 31, and Bluetooth/Wi-Fi need to be on. The hint that appears blames the other phone.
**Recommendation:**
- Before discovering or advertising, check `LocationManagerCompat.isLocationEnabled` (on API ≤ 30), Bluetooth adapter state and Wi-Fi state.
- Show a specific fix card with a button that opens the right settings panel (`Settings.Panel.ACTION_WIFI`, `ACTION_LOCATION_SOURCE_SETTINGS`, `BluetoothAdapter.ACTION_REQUEST_ENABLE`).
- Show the same card on the Camera side when advertising fails.

### P12 — Hard to read in bright sun: no brightness boost on the Remote, faint text at 3:1 contrast (Medium)
**Where:** `RemoteScreen` keeps the screen on but never raises brightness. The UI is near-black "ink". `PoseColors.PaperFaint` (38 % alpha, about **3.1:1** on Ink) is used for captions such as "Applies from the next session", "On both phones", the fps readout, "Tap to wake" and turned-off SafeLines. `Overline` is 10.5 sp.
**Persona:** anyone outdoors (the core use case is a rock at a viewpoint).
**Recommendation:**
- While the Remote is Live, set `window.attributes.screenBrightness = 1f` by default, with a small sun toggle to turn it off.
- Raise PaperFaint to at least 0.55 alpha (≥ 4.5:1) for any text.
- Give notice pills an opaque background in bright scenes.

### P13 — Settings can't be reached during a session, and the Remote never sees the Camera's safe-mode state (Medium)
**Where:** Settings is reachable only from Home (`AppRoot`). The Camera screen has only Leave and Lock. Settings says "Applies from the next session".
**Persona:** solo traveller who wants a longer auto-disconnect halfway through a shoot. Today they have to leave, which ends the session, change the setting and pair again (with Allow).
**Recommendation:**
- Add a small settings sheet on the Camera screen, shown only while no Remote is connected or from the "Session ended" card, with the auto-disconnect length.
- Show the Camera's effective timeout and safe-mode state on the Remote (tap the link pill), using the `CameraStatus` fields that are already sent.

### P14 — The phone's name is broadcast and can't be changed, and identical phones can't be told apart (Low-Medium)
**Where:** `NearbyLink.deviceName` comes from `Settings.Global "device_name"`, which is often "Oz's Galaxy S24", and is advertised to every phone nearby running the app. It is also what the LIVE badge shows.
**Persona:** couple or family with two "Pixel 8" phones (hard to pick the right one), and privacy-minded users.
**Recommendation:** Add an editable "Name shown to other phones" setting (default = model name without the owner's name), and disclose it in the privacy summary (P3).

### P15 — English-only: strings are hard-coded and RTL is off, so no Hebrew or other languages (Medium, market)
**Where:** Almost every string is a Kotlin literal; `strings.xml` holds only 5. The manifest has `supportsRtl="false"`, and the README says "English-only, always LTR".
**Persona:** Hebrew/Arabic speakers, and non-English travellers generally (the target market is tourists).
**Recommendation:**
- First step, which is cheap and doesn't change behaviour: move every string into `strings.xml` (including plurals for "N of M" and durations). That unblocks translation.
- Then turn on `supportsRtl` and keep the forced-LTR only where it matters (`CodeDigits` already does this, and the viewfinder).
- Hebrew is a natural first locale if it matches the launch market.

### P16 — Play Console items: FGS declaration, target audience, Data safety, large-screen orientation (Low-Medium)
Checked OK:
- The FGS type `connectedDevice` has a qualifying permission (`CHANGE_NETWORK_STATE`, BT permissions).
- The Camera pauses when backgrounded, so no `camera` FGS type is needed.
- `BLUETOOTH_SCAN` and `NEARBY_WIFI_DEVICES` use `neverForLocation`, and location is capped at API 32 with an on-screen reason.
- `allowBackup=false`.
- No network or analytics SDKs.

Still to do before submitting:
1. **FGS declaration** in the Console: a video of both roles, with a justification that the service keeps the phone-to-phone link during a user-started shoot. The Remote starts the service while *discovering*, before any link exists, so be ready to justify that or start it only after connecting.
2. **Target audience 13+ (ideally 18+)** to stay out of the Families policy, given the surveillance risk (P1) and the planned ads (`AdSlot`).
3. **Data safety:** "No data collected / shared" is accurate today but becomes wrong once an ad SDK is wired in. Declare the device name broadcast in the policy text.
4. `screenOrientation="portrait"` is ignored on large screens from targetSdk 36. Check the layouts on a tablet or foldable.
5. The notification text "Camera is live — waiting for your Remote" doesn't change once a Remote connects. Update it to "LIVE · viewed by <Remote>" so the shade is honest too.

### P17 — A bystander who spots LIVE has no quick way to stop it (Low-Medium)
**Where:** `CameraScreen` lock overlay (hold 1.1 s to unlock, then find the X) and the dimmed standby.
**Persona:** a person who notices a propped phone showing LIVE.
**Recommendation:** Add a visible **"Stop"** chip next to the LIVE badge that ends the session with one tap (`endSession("Stopped on the Camera")`). It stays reachable while locked, because the lock is there to stop accidental *changes*, not to stop the session from ending.

### P18 — No help, troubleshooting or feedback entry point (Low)
**Where:** Home shows "How it works" (the 3-panel tutorial only) and Settings. Nothing covers "can't find Camera", "photo didn't arrive", "session keeps ending", and there's no contact or feedback link.
**Recommendation:** Add a short FAQ screen, reusing the P11 checks, plus a "Send feedback" `mailto:` link that includes the version (P3).

---

## Checked and OK (not findings)
- The consent screen can't be skipped: `startRole` routes to `Consent(role)` until `prefs.consented`, and Back returns to Home. It can be re-read from Settings.
- Safe mode enforces a minimum auto-disconnect: `effectiveTimeout` coerces 0 to 60, the "Off" chip is disabled, and the stricter of the two phones' settings wins on the Camera.
- After a timeout the Camera stops advertising, the Remote doesn't retry (`LinkPhase.Ended`), and "Start again" is needed on the Camera.
- The Camera pauses capture on `ON_STOP`, so photos can't be taken in the background.
- Permissions: one screen with one reason per group, an "Open settings" fallback, and no camera permission requested for the Remote role.
- The volume-key shutter is a good motor-accessibility aid. The shutter has a dynamic contentDescription ("Cancel countdown").
- A storage-full check blocks the shutter before the countdown, and the Remote gets told.
- The pairing code is forced LTR, so the two screens agree on RTL phones.

## Out of scope and worth noting (post-MVP wishlist, not ranked)
- Interval or burst-series mode (N shots every X s), so a solo traveller can try several poses without walking back.
- Pinch zoom on the live view; timers of 15 or 20 s for longer walks into frame.
- The spec's post-MVP ideas (pose ghost overlay, auto-capture, watch remote, iPhone) all build on P4 (photo pipeline) and P13 (in-session settings), so do those first.
