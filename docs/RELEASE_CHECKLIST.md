# Release checklist — Google Play

## Done in the code / CI ✅
- [x] Targets **Android 16 (API 36)**: Play's requirement for new apps and updates since 31 Aug 2026 (AGP 8.13, Gradle 8.13).
- [x] Builds an **Android App Bundle** (`bundleRelease`), uploaded by CI as the `holdthatpose-play-bundle` artifact with the R8 `mapping.txt`.
- [x] **Release signing** from GitHub secrets (falls back to the debug key, with a CI warning, until the secrets exist).
- [x] **16 KB page-size check** in CI (`scripts/check-16kb.sh`): fails the build if any 64-bit native library isn't 16 KB-aligned.
- [x] **Privacy policy**, in-app (Settings → Privacy policy) and as a web page (`docs/legal/privacy-policy.md`).
- [x] Store **icon 512×512** and **feature graphic 1024×500**, rendered by CI into `docs/store/`.
- [x] Store listing copy drafted: `docs/store-listing.md`. Data safety answers drafted: `docs/play-console-privacy.md`.
- [x] Permissions explained in-app. Foreground service typed `connectedDevice`, with a user-visible notification and Stop.
- [x] Backups exclude pairing trust and consent. No ads, no analytics, no accounts.

## You need to do 🔲

### 1. Accounts & legal
- [ ] Create or verify the **Google Play developer account** ($25 one-off) and complete **identity verification**
      (organisations need a D-U-N-S number).
- [ ] Make the **EU Digital Services Act trader / non-trader declaration** in Play Console. Without it the
      app isn't shown in the EU.
- [ ] Add a **developer contact email** to the store listing (Play requires it), and tell me if you want it in the privacy policy too.
- [ ] Optional but recommended: trademark search for "Hold That Pose"; legal review of the privacy policy.

### 2. Signing key (once; keep it safe forever)
- [ ] Create the upload key on your computer:
      `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
- [ ] Add 4 GitHub secrets (repo → Settings → Secrets and variables → Actions):
      `RELEASE_KEYSTORE_BASE64` = output of `base64 -w0 upload.jks` (macOS: `base64 -i upload.jks`),
      `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` (`upload`), `RELEASE_KEY_PASSWORD`.
- [ ] Back up `upload.jks` and its passwords offline. Never commit it.
- [ ] On first upload, accept **Play App Signing** (Google holds the app signing key; yours is only the upload key).

### 3. Publish the privacy policy
- [ ] GitHub → Settings → **Pages** → Deploy from a branch → the branch with this code, folder **/docs**.
- [ ] Check that https://ozsoreq.github.io/RemoteCam/legal/privacy-policy.html loads, then paste it into
      Play Console → App content → Privacy policy.

### 4. Test on real phones
- [ ] **Two-phone test** of pairing, Allow, live view, countdown, photo transfer, reconnect, delete-on-both
      and the 30-minute check-in. None of these have run on hardware yet.
- [ ] Try one **tablet or foldable**: Android 16 ignores portrait lock on large screens, so check the landscape layout.
- [ ] Try one phone **without Google Play services**, or with it disabled: the app should explain, not crash.

### 5. Closed testing (personal accounts created after 13 Nov 2023)
- [ ] Upload the signed `.aab` to a **closed testing** track.
- [ ] Recruit **≥ 12 testers** who stay opted in for **14 continuous days**, then apply for production access
      on the Play Console dashboard. Testers need two phones, or can pair up.

### 6. Store listing (Play Console → Grow → Store presence)
- [ ] Paste the title, short and full description from `docs/store-listing.md`.
- [ ] Upload `docs/store/play_icon_512.png` and `docs/store/play_feature_graphic_1024x500.png`.
- [ ] Upload **2–8 phone screenshots**. Use real two-phone shots (Remote live view, countdown, review, Camera
      with LIVE and Allow), not the emulator's fake camera feed.
- [ ] Category: Photography. Contact email. Optionally a short promo video.

### 7. App content declarations (Play Console → Policy → App content)
- [ ] Privacy policy URL (step 3).
- [ ] **Data safety**: use `docs/play-console-privacy.md`, and check Google's disclosure for `play-services-nearby`.
- [ ] **Ads**: No.
- [ ] **App access**: "All functionality available without special access", plus reviewer notes:
      *"Needs two Android phones with Google Play services. Open the app on both, choose Camera on one and
      Remote on the other, tap the Camera in the list, confirm the code, then tap Allow on the Camera."* Link a short demo video.
- [ ] **Content rating** questionnaire (IARC). Expected: Everyone / PEGI 3; no user-generated content is shared publicly.
- [ ] **Target audience**: 13+ (don't include under-13 age groups).
- [ ] **Foreground service permissions**: declare `connectedDevice`. Describe it as "keeps the direct link to the
      user's other phone alive during a shooting session", and add a **video link** showing a session starting.
- [ ] News app: No · Health: No · Financial features: No · Government: No.

### 8. Every release
- [ ] Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`.
- [ ] CI green (build, unit tests, emulator tests, 16 KB check, and the signing step says "ready for Play").
- [ ] Download the `holdthatpose-play-bundle` artifact and upload the `.aab`, plus `mapping.txt` for readable crash reports.
- [ ] Check the **pre-launch report** for crashes and accessibility warnings before promoting to production.
