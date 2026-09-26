# Privacy Policy

Hold That Pose — remote selfie camera for Android
Effective date: 26 September 2026

## In short

- No accounts, no sign-in, no servers. The app has no backend and never uploads anything.
- No ads, no analytics, no tracking, and no third-party advertising or analytics SDKs.
- Your photos stay on your own two phones. They travel directly between them over an encrypted local link and never go through us or the internet.
- We (the developer) do not collect, store, sell or share any personal data.

## Who we are

Hold That Pose ("the app", "we", "us") is published on Google Play by the developer shown on the app's store listing. This policy explains what the app does with information on your devices. It applies to the Android app only.

## What the app handles, and where it stays

Everything below is processed on your devices. None of it is sent to the developer.

### Photos

- The phone you set as the Camera takes the photo and saves the full-resolution image to its own gallery, in the "Pictures/Hold That Pose" album.
- A smaller review copy (about 2 megapixels) is sent directly to the phone you set as the Remote and saved in its gallery in the same album.
- Review copies are re-encoded and carry no location or camera metadata. The app does not add your location to any photo.
- Photos stay in your gallery until you delete them, in the app or in your gallery app. "Delete on both" also asks the Camera phone to delete its copy during the same session.

### Live preview

The Camera streams a low-resolution live view to the Remote so you can frame the shot. Preview frames are shown on screen and discarded. They are never saved, and screen capture is blocked while the live view is on screen.

### Device name and pairing information

- To let the two phones find each other, the Camera and the Remote announce themselves to nearby devices using your phone's name (as set in Android settings) and a random identifier created when you install the app. The identifier is not linked to you, your account or your phone's hardware.
- These announcements are only visible to nearby phones running Hold That Pose, and only while the Camera or pairing screen is open.
- Connections are confirmed with a 4-digit code shown on both screens and, in safe mode, an "Allow" tap on the Camera phone.
- The link between the phones is set up by Google Play services (Nearby Connections) and is encrypted.

### Settings stored on your phone

The app stores a few preferences on your phone: your last role (Camera or Remote), the name and random identifier of the phone you last paired with, viewfinder options, safe-mode and auto-disconnect settings, and whether you have seen the tutorial and accepted the responsible-use terms. They are excluded from cloud backup and device transfer, and are deleted when you clear the app's data or uninstall it.

## Permissions and why they are needed

- Camera: only on the phone used as the Camera, to show the viewfinder and take photos.
- Nearby devices (Bluetooth scan, advertise and connect; nearby Wi-Fi devices): to find and connect your two phones directly, without the internet.
- Location (Android 12 and older only): older Android versions require it to scan for nearby Bluetooth and Wi-Fi devices. The app never reads, stores or shares your location.
- Photos and storage (Android 8 and 9 only): to save photos to your gallery.
- Notifications: to show that a shooting session is running and to offer a Stop button.
- Foreground service, wake lock and vibration: to keep the session connected while you pose, and for haptic feedback.
- Hide overlay windows: to stop other apps from drawing over the Camera screen and its Allow prompt.

You can deny or revoke any permission in Android settings. Features that depend on it will stop working, and the rest of the app keeps working.

## Third-party services

- Google Play services (Nearby Connections) sets up the direct link between your phones. Google may process limited technical and diagnostic information about your device as part of Google Play services, under Google's Privacy Policy: https://policies.google.com/privacy
- The app uses open-source libraries (such as Android Jetpack and CameraX) that run entirely on your device and do not send data anywhere.
- The app contains no advertising. If ads are ever added, this policy will be updated before that version is released, and ads will never appear while you are shooting.

## Data sharing and sale

We do not collect personal data, so there is nothing for us to share or sell. The only transfer the app makes is the direct, encrypted transfer of preview frames and photos between the two phones you choose to connect.

## Your control over your data

Because all data stays on your devices, you are in full control:
- Delete photos in the app's review screen or in your gallery.
- Clear the app's settings by clearing its data or uninstalling it.
- Revoke permissions in Android settings at any time.

The developer holds no personal data about you, so there is nothing for us to access, correct, export or delete on request. You can still contact us with any privacy question.

## Safety features

Safe mode is on by default. It requires approval on the Camera phone for each new Remote, keeps a visible LIVE sign and sounds on the Camera, disconnects idle sessions automatically and checks in after 30 minutes. Some of these protections stay on even when safe mode is off. Please follow the responsible-use terms shown in the app and only photograph people who agree.

## Children

The app is not directed at children under 13, and we do not knowingly collect personal information from anyone, including children.

## Security

Photos and preview frames travel only over the encrypted link between your two phones. The app refuses transfers it did not request, limits their size, and ignores malformed data from other devices. No data is stored on any server.

## Changes to this policy

If this policy changes, the new version will be published at the same address and in the app, with a new effective date. Material changes, such as adding advertising, will be made before the version that introduces them is released.

## Contact

For privacy questions or requests, open an issue at https://github.com/ozsoreq/RemoteCam/issues or use the developer contact details on the app's Google Play listing.
