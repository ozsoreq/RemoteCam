# Hold That Pose — remote selfie camera

Two phones, one camera. Prop one phone on a rock (the **Camera**), keep the other in your
hand (the **Remote**), watch yourself live, and tap the shutter. Built from the
*Remote Selfie Camera — MVP Product Spec*.

App ID: `app.holdthatpose`. The display name lives in `app/src/main/res/values/strings.xml`.

## What's in the MVP

| # | Spec feature | Where |
|---|---|---|
| 1 | Role picker (Camera / Remote), last role remembered, 3-panel first-run how-to | `ui/home/` |
| 2 | One-tap offline pairing, 4-digit code on both screens, auto-reconnect to last phone | `net/NearbyLink.kt`, `ui/pairing/` |
| 3 | Live preview on Remote (adaptive JPEG, ~20 fps on Wi-Fi, 8 fps/320 px on Bluetooth) | `session/CameraSession.kt#onFrame` |
| 4 | Shutter + countdown 0/3/5/10 s, beeps + flashing screen, volume-key shutter | `session/*Session.kt`, `ui/camera/`, `MainActivity` |
| 5 | Full-res capture on Camera, saved to its gallery (`Pictures/Hold That Pose`) | `session/CameraController.kt`, `media/PhotoStore.kt` |
| 6 | ~2 MP copy sent to Remote, saved there too; review with Keep / Delete (both phones) / Retake | `ui/remote/ReviewScreen.kt` |
| 7 | Lens switch: ultrawide (0.5×) / main / front — ultrawide is the default | `CameraController` |
| 8 | Tap-to-focus/expose on the Remote preview (and on the Camera viewfinder) | `CameraController#focusUpright` |
| 9 | Rule-of-thirds grid + horizon level (turns red when the Camera is bumped) | `ui/components/Controls.kt` |
| 10 | Connection (signal bars, RTT, Bluetooth fallback label) + battery for the far phone | `ui/remote/RemoteScreen.kt` |

Also from the competition review: mirrored preview (default on), best-of-3 mini-burst
picked by sharpness, foreground service so notifications never drop the link, queued
shutter through reconnects, and a QR code to install the app on the second phone.

Edge cases from the spec: 30 s reconnect window, capture survives link drops (photo syncs
on reconnect), low-battery warning (<10 %) and preview auto-stop (<5 %), heat → 10 fps,
storage-full blocks the shutter before you pose, paused session on incoming calls, and a
single permission screen with an "Open settings" fallback.

## Safe mode (on by default)

A remote camera can be misused as a hidden camera, so Hold That Pose ships with guardrails that make
that hard and make intent visible. Configure in **Settings**.

| Guardrail | Behaviour |
|---|---|
| Responsible-use agreement | Shown once before the first session; re-readable from Settings. |
| Camera-side approval | A new Remote connects only after **Allow** is tapped on the Camera phone (code shown on both). Only the Remote already in session may silently reconnect after a short drop. |
| Auto-disconnect | The **Camera** drops the session after X s without a Remote command (default 60 s; 30 s – 10 min). The Remote gets a 10 s "Keep going" warning. Enforced on the Camera, using the stricter of both phones' settings. Can't be turned off in safe mode. |
| No silent resume | After a timeout the Camera stops advertising and the Remote doesn't retry; someone must tap **Start again** on the Camera phone. |
| LIVE sign | A red LIVE badge with the Remote's name is always on the Camera screen, above the dim and lock overlays. |
| Audible cues | Chime on the Camera when a Remote connects or the session ends; countdown and shutter beeps can't be muted. |
| Visible only in foreground | The Camera pauses when the app isn't on screen; photos can't be taken in the background. |
| Local only | No accounts, no uploads; photos exist only on the two phones. |

This is product-level risk reduction, not legal advice — have a lawyer review the store
listing, privacy policy and terms before launch (Play requires a privacy policy for apps
using the camera).

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
never queue behind video. Photos go as a STREAM payload with an inline JSON header.

The spec suggests H.264 via MediaCodec for the preview; this MVP uses the "simpler first
prototype" it mentions — adaptive-quality JPEG frames — which keeps latency flat and the
code small. Swapping in H.264 only touches `CameraSession#onFrame` and the Remote decoder.

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
