# Play Console: privacy setup

## 1. Publish the policy (one-time, ~1 minute)
The policy lives in `docs/legal/privacy-policy.md`. It is bundled into the app (Settings → Privacy policy)
and published on the web from the same file.

1. On GitHub: **Settings → Pages → Build and deployment → Source: Deploy from a branch**.
2. Branch: the branch that holds this code (e.g. `main` after merging), folder: **/docs**. Save.
3. After a minute the policy is live at
   **https://ozsoreq.github.io/RemoteCam/legal/privacy-policy.html**. This is the URL in
   `app/src/main/res/values/strings.xml` (`privacy_policy_url`). If you host it elsewhere, change it there.

Paste the same URL into **Play Console → App content → Privacy policy**.

## 2. Data safety form (suggested answers — review against Google's current guidance)
| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | No servers, accounts, ads or analytics. Nothing is sent to the developer or any third party. |
| Photos | Not collected | Photos are sent only between the user's own two phones, at the user's request, over an encrypted direct link. They never leave those devices for the developer or a third party. |
| Device or other IDs | Not collected | The random pairing ID and device name are broadcast locally to nearby phones for pairing. They are never transmitted to the developer. |
| Is data encrypted in transit? | Yes | Nearby Connections encrypts the phone-to-phone link. |
| Can users request deletion? | Not applicable | The developer holds no user data; everything is deleted on-device (photos in the gallery, settings by clearing data or uninstalling). |

**Check before submitting:** Google Play services (Nearby Connections) is a Google SDK. Review Google's
published data-safety guidance for `play-services-nearby`, and include anything it says the SDK collects.

## 3. Other App content items
- **Target audience:** 13+ (the policy says the app is not directed at children under 13).
- **Ads:** "No, my app does not contain ads" (update both this and the policy before adding an ad SDK).
- **Foreground service declaration:** type `connectedDevice`. It keeps the link to the user's other phone
  alive during a shooting session, and it's user-visible with a Stop action.
- **Contact:** the policy points to GitHub issues and the store listing. Add a developer email in the
  Play listing, which Play requires anyway.
