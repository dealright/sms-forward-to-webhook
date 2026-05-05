# App-store submissions

How to get sms-forward-to-webhook listed on third-party Android stores.
Each section is a separate, independent path — start with whichever you want users on first.

## 1. IzzyOnDroid (fastest, ~days)

IzzyOnDroid is a third-party F-Droid-compatible repo. They pull our **already-signed** APK from GitHub Releases — no rebuild on their server. This is the lowest-effort path.

**You need:** a Codeberg account (https://codeberg.org/user/sign_up). It's free, no email verification grief.

**Steps:**

1. Sign up / sign in at https://codeberg.org.
2. Open https://codeberg.org/IzzyOnDroid/repo/issues.
3. Click **New Issue**.
4. Title: `App request: SMS Forward to Webhook (com.dealright.smsforwarder)`
5. Body — paste this:

   ```
   Application ID: com.dealright.smsforwarder
   App name: SMS Forward to Webhook
   Source code: https://github.com/dealright/sms-forward-to-webhook
   License: Apache-2.0
   Latest release: https://github.com/dealright/sms-forward-to-webhook/releases/latest
   APK signing: signed with our own release key (consistent across releases)
   Description: Forwards incoming SMS to any webhook URL as configurable JSON.
   No tracking, analytics, or third-party network calls beyond the user-configured webhook.
   ```

6. Submit. Maintainer (IzzySoft) usually responds within a few days. They may ask for the SHA-256 of the signing certificate — you can get it with:

   ```bash
   keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
   ```

7. Once added, the app shows up at `https://apt.izzysoft.de/fdroid/index/apk/com.dealright.smsforwarder`.

---

## 2. F-Droid official (slowest, ~weeks)

The official F-Droid repo. They **rebuild from source** on their server and sign with the F-Droid key — meaning users who install via F-Droid can't update from the GitHub APK without uninstalling first (different signing key). Worth it for the audience.

**You need:** a GitLab account (https://gitlab.com/users/sign_up). Free.

**Steps:**

1. Sign up / sign in at https://gitlab.com.
2. Fork the F-Droid data repo: https://gitlab.com/fdroid/fdroiddata → click **Fork**.
3. In your fork, create a new file at `metadata/com.dealright.smsforwarder.yml`. Use the contents of [fdroid-metadata.yml](fdroid-metadata.yml) in this directory as the starting point.
4. Verify the build locally first (optional but strongly recommended). Install `fdroidserver` (`pipx install fdroidserver`) and run:

   ```bash
   fdroid build --on-server --no-tarball com.dealright.smsforwarder
   ```

   If it fails, fix the metadata or build script before submitting.

5. Commit the new file in your fork on a new branch (e.g. `add-sms-forward-to-webhook`).
6. Open a Merge Request from your fork's branch back to `fdroid/fdroiddata` `master`. Title: `New app: SMS Forward to Webhook (com.dealright.smsforwarder)`.
7. The F-Droid bot runs the build automatically. If it succeeds, a maintainer reviews and merges within 1–4 weeks.
8. After merge, the app appears at `https://f-droid.org/packages/com.dealright.smsforwarder/` on the next index publish (typically 1–2 days).

**Common gotchas:**
- F-Droid won't sign with our keystore — they use their own. Make sure `assembleRelease` builds an unsigned APK successfully (the current `app/build.gradle.kts` already handles this — the `signingConfig` block is skipped when `keystore.properties` is absent).
- If we add new Gradle plugins or change minSdk/targetSdk, the metadata file may need updating.

---

## 3. Accrescent (later)

Accrescent is a security-focused store. Requires **reproducible builds** (ours aren't yet — current build is deterministic enough for F-Droid acceptance, but full reproducibility means stripping timestamps, locking dependencies, and building bit-identical APKs across machines). Defer until we have user demand.

When we're ready: https://github.com/accrescent/submissions

---

## 4. Obtainium (no submission required)

Already works — users add `https://github.com/dealright/sms-forward-to-webhook` to Obtainium and get auto-updates from GitHub Releases. No action on our end. Already documented in the README.

---

## Skipped: Google Play Store

Won't work. Play policy restricts `READ_SMS` / `RECEIVE_SMS` to apps that are the default SMS handler or have an approved exemption. SMS-to-webhook forwarders are routinely rejected, and appeals rarely succeed.

---

## Releasing a new version

For each new release, the per-store cadence is:

- **GitHub Releases** — tag and push as today; `Obtainium` users update automatically within hours.
- **IzzyOnDroid** — auto-detects new releases on GitHub usually within 24h. No action.
- **F-Droid official** — auto-updates because `AutoUpdateMode: Version` and `UpdateCheckMode: Tags` are set in the metadata. As long as the version tag follows the `vX.Y.Z` pattern and the build succeeds, no MR needed for updates.
- **Accrescent** — manual upload each release (when set up).
