# Hold That Pose — remote selfie camera

Two phones, one camera. Prop one phone on a rock (the **Camera**), keep the other in your
hand (the **Remote**), watch yourself live, and tap the shutter. Built from the
*Remote Selfie Camera — MVP Product Spec*.

App ID: `app.holdthatpose`. The display name lives in `app/src/main/res/values/strings.xml`.

## What's in the MVP

| # | Spec feature | Where |
|---|---|---|
| 1 | Role picker (Camera / Remote), last role remembered, 3-panel first-run how-to | `ui/home/` |
| 2 | One-tap offline pairing, 4-digit code on both screens, one-tap "Reconnect" to the last phone, automatic reconnect during a session | `net/NearbyLink.kt`, `ui/pairing/` |
| 3 | Live preview on Remote (adaptive JPEG, ~20 fps on Wi-Fi, 8 fps/320 px on Bluetooth) | `session/CameraSession.kt#onFrame` |
| 4 | Shutter + countdown 0/3/5/10 s, beeps + flashing screen, volume-key shutter (live view only) | `session/*Session.kt`, `ui/camera/`, `MainActivity` |
| 5 | Full-res capture on Camera, saved to its gallery (`Pictures/Hold That Pose`) | `session/CameraController.kt`, `media/PhotoStore.kt` |
| 6 | ~2 MP copy sent to Remote (length + CRC checked, acknowledged, resent after a drop), saved there too; review with Keep / Share / Delete (here or on both phones, with Undo) / Retake | `ui/remote/ReviewScreen.kt` |
| 7 | Lens switch: ultrawide (0.5×) / main / front — ultrawide is the default | `CameraController` |
| 8 | Tap-to-focus/expose on the Remote preview (and on the Camera viewfinder) | `CameraController#focusUpright` |
| 9 | Rule-of-thirds grid + horizon level (turns red when the Camera is bumped) | `ui/components/Controls.kt` |
| 10 | Connection (signal bars, RTT, Bluetooth fallback label) + battery for the far phone | `ui/remote/RemoteScreen.kt` |

Also from the competition review: mirrored preview (default on), best-of-3 mini-burst
picked by sharpness, foreground service so notifications never drop the link, queued
shutter through reconnects, and a QR code to install the app on the second phone.

Edge cases from the spec: 30 s reconnect on the Remote (retried until it succeeds, and it
says so when the Camera needs **Allow** again), capture survives link drops (the photo syncs
on reconnect and stays queued until the Remote confirms it), low-battery warning (<10 %) and
preview auto-stop (<5 %), heat → 10 fps, storage-full blocks the shutter before you pose,
paused session on incoming calls, a single permission screen with an "Open settings"
fallback, and "Turn on Bluetooth / Location" hints when a radio is off.

## Guardrails and safe mode

A remote camera can be misused as a hidden camera, so Hold That Pose ships with guardrails that make
that hard and make intent visible. Most of them are **always on**; safe mode (on by default,
in **Settings**) adds a session limit and keeps the LIVE sign readable. Turning safe mode off
needs a confirmation.

| Guardrail | Behaviour | Safe mode off |
|---|---|---|
| Responsible-use agreement | Shown once before the first session; re-readable from Settings. | Same |
| Camera-side approval | A new Remote connects only after **Allow** is tapped on the Camera phone (code shown on both). Only the Remote already in session may reconnect without Allow, and only within 45 s of a drop (with a soft chime). A second phone can't interrupt a handshake; unanswered handshakes expire after 60 s. | Same |
| LIVE sign | A red LIVE badge with the Remote's name and the session time is always on the Camera screen, above the dim and lock overlays, with a **Stop** button anyone near the phone can tap. The notification also says who is watching and has **Stop**. | Same, but it may dim to near-black |
| Audible cues | Chime on the Camera when a Remote connects or the session ends. Countdown and shutter beeps play on the alarm stream; the Camera raises a quiet alarm volume to 50 % while it runs (restored afterwards) and shows "Sound off" (and the Remote "Camera is muted") when the cues can't be heard. | Same |
| Auto-disconnect | The **Camera** drops the session after X s without a deliberate Remote action (shutter, lens, focus, delete, "Keep going"; pings don't count) — default 60 s, 30 s – 10 min, never off. The Remote gets a 10 s "Keep going" warning. Enforced on the Camera, using the stricter of both phones' settings. | Maximum 10 min |
| Session limit | After 30 min the Camera chimes and asks "Still OK?"; without **Continue** on the Camera within 60 s the session ends (never mid-shot). | Off |
| No silent resume | After a timeout or Stop the Camera stops advertising and the Remote doesn't retry; someone must tap **Start again** on the Camera phone. | Same |
| Owned photos | A Remote only receives, and can only delete, photos it took in the current session. Delete defaults to "Delete here"; "Delete on both" is a separate choice, both with a 5 s Undo. | Same |
| Visible only in foreground | The Camera pauses when the app isn't on screen; photos can't be taken in the background. Other apps' overlays are hidden over the Camera screen. | Same |
| No screen capture of the live view | The Remote's live view is `FLAG_SECURE` (Review stays shareable). | Same |
| Local only | No accounts, no uploads; photos exist only on the two phones. Pairing trust isn't copied to a new phone by backup or device transfer. | Same |

This is product-level risk reduction, not legal advice — have a lawyer review the store
listing, privacy policy and terms before launch (Play requires a privacy policy for apps
using the camera).

## Privacy

The privacy policy is `docs/legal/privacy-policy.md`, one file used in two places: it is bundled into the
app (Settings → Privacy policy, readable offline) and published on the web via GitHub Pages at
https://ozsoreq.github.io/RemoteCam/legal/privacy-policy.html. See `docs/play-console-privacy.md` for the
one-time Pages setup and suggested Play Console Data safety answers. A unit test keeps the required
sections from going missing.

## Design

Dark "ink" surfaces, soft off-white type and one blue accent (sky → azure)
reserved for what matters: the shutter, the countdown and the active state. Titles and the
countdown numerals use **Instrument Serif**; UI text uses **Manrope** (both SIL OFL, see
`licenses/`). Controls are frosted-glass pills with hairline borders and spring-press
feedback; icons are a custom 1.6-stroke set (`ui/icons/PoseIcons.kt`) so nothing looks
stock. Screens carry as little text as possible; explanations live in the first-run tutorial.
The UI is English-only and always laid out left-to-right, including on RTL phones.

## Architecture

```
MainActivity ── AppRoot (screen state machine)
                 ├─ Home / Onboarding / Permissions
                 ├─ CameraScreen  ─┐
                 ├─ RemotePairing  ├─ PoseApp (service locator)
                 └─ RemoteScreen  ─┘    ├─ NearbyLink    Google Nearby Connections, P2P_POINT_TO_POINT
                                        ├─ CameraSession countdown, burst, save, stream, status
                                        ├─ RemoteSession frames, commands, reconnect, photos
                                        └─ SessionService foreground service (connectedDevice)
```

Wire protocol (`net/Protocol.kt`): BYTES payloads tagged `1` = JSON command, `2` = preview
frame (roll, flags, JPEG). Frames use one-in-flight back-pressure so commands (shutter!)
never queue behind video. Photos go as a STREAM payload with an inline JSON header carrying
the length and CRC32 (max 16 MB); the Remote answers `pack` (PhotoAck) and the Camera keeps
each copy in a per-Remote outbox (`session/PhotoOutbox.kt`) until then. Pure session rules
(re-entry window, session limit, which commands count as activity, photo acceptance, error
texts) live in `session/SessionRules.kt` and are unit-tested.

The spec suggests H.264 via MediaCodec for the preview; this MVP uses the "simpler first
prototype" it mentions — adaptive-quality JPEG frames — which keeps latency flat and the
code small. Swapping in H.264 only touches `CameraSession#onFrame` and the Remote decoder.

Settings → About shows the version, the privacy policy link (`privacy_policy_url` in
`strings.xml` is a placeholder the owner must replace before release) and the licences.

Ads: `AdSlot()` marks the only allowed placements (Home, Pairing). No ad SDK is bundled
yet, keeping the APK small.

## Build

Requirements: JDK 17, Android SDK 35.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # minified, signed with the debug key for sideloading
```

CI (`.github/workflows/android.yml`) builds both APKs on every push and uploads them as the
`holdthatpose-apks` artifact. Min Android 8.0 (API 26). Requires Google Play services on both phones
(for Nearby Connections).
