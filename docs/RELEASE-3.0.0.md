# LifePlanner v3.0.0 — Release Runbook

App id `az.tribe.lifeplanner` · versionName **3.0.0** · Android versionCode **11** · iOS build **8**
Supabase project `rkdggdfabwgukspylybu`. Prepared 2026-07-25, **re-verified 2026-08-20**.

One thing stands between this and an upload, and it is yours: the **keystore passwords**
(blocker 1). The merge to `main` (old blocker 3) is **done**. Everything else below is done.
After the upload, the Play Console submission itself is a console action, not a build step.

Version numbers now live in one place: `gradle/libs.versions.toml` (`app-versionName` /
`app-versionCode`). Android's `versionName`/`versionCode` and `BuildKonfig.APP_VERSION` all read
from there. iOS still carries its own `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` in
`project.pbxproj` — bump those by hand to match.

## What's in v3 (since the 2.3 on the stores)
Pillar 6 Possibility Mode (coach hand-off + instant options), the For You home feed, the
goal-as-journey redesign, habit track modes + health auto-complete, device calendar + steps on
home, the **Learn hub** (lesson paths, synced progress, recommendations), Supabase-native
monitoring, and — added after this runbook was first written — Life Balance folded into the
Today feed, a habit practice ground, journal entries following the hub's day lens, and
seconds as a first-class habit duration.

---

## ✅ Verified 2026-08-03 (code + backend ready)
- **Release AAB builds clean** — `:app:androidApp:bundleRelease` succeeds (R8 + resource shrink + lint-vital). ~50 MB. Unsigned locally (see signing below).
- **Unit tests green** — 1167 host tests pass, 0 failures (`:app:shared:testAndroidHostTest`, 2026-08-20).
- **The 21→39 migration chain is covered** — `DatabaseMigrationsTest` runs the real chain against a hand-written v21-era database (the shape 2.3 shipped) and asserts on the resulting schema, since `addColumnSafe` swallows exceptions and a failed step is otherwise silent until a user hits `no such column`. Covers the columns current queries read, the create-before-alter ordering for `DecisionEntity`, the Learn hub tables, idempotency (the chain runs on *every* open), and row preservation. Still a JVM schema test, not a 2.3-install-upgraded-on-device run.
- **DB schema v42** (v36 when this doc was written, v39 at the 2026-08-04 pass; the Learn hub content cache, journal-detected decisions, habit `completionSource`, then the Wheel of Life tables landed since). Runtime Android migrations exist through `migrateToVersion42` in `DatabaseMigrations.kt` and every one of them is wired into the `runAndroidMigrations` chain called from `DatabaseDriverFactory.onOpen()` (re-verified 2026-08-20); `.sqm` files through 42 present for iOS + compile-time verification. 2.3 shipped at schema v21, so upgraders run 21→42 with every step present.
- **Learn content degrades safely** — `KnowledgeFetcher` publishes bundled lessons first, then the local cache, then Supabase, and refuses to publish an empty remote result. The Learn hub is never blank, even if `knowledge_lessons` is unseeded.
- **iOS `GoogleService-Info.plist` is present** in the checkout (was a blocker; resolved).
- **Illustration licensing cleared** — Kamran confirmed on the UI8 account (2026-08-03) that the Dotion tier covers use in a store-distributed app. 24 of the 30 shipped illustrations come from that pack; the 5 `illus_learn_*` are hand-authored. Worth filing the licence PDF in `../lifeplanner-assets` so the next release does not have to re-establish this.
- **iOS entitlements / Info.plist** validated (`plutil -lint`). Unused HealthKit entitlements were removed earlier; the unused `NSHealthUpdateUsageDescription` was removed 2026-08-03 (the app requests HealthKit **read-only** — `writeTypes = emptyList()`).
- **Backend deployed** — 7 edge functions ACTIVE, `knowledge_reads` table + RLS live, monitoring cron jobs (`lifeplanner-health` 5 min, `lifeplanner-store-watch` 30 min) active.
- **iOS location permission reaches the screen** (fixed 2026-08-04). Granting it left the weather card on "Enable" until you left and re-entered the screen: `CLLocationManager.delegate` is a weak reference, and the delegate was built inside `DisposableEffect`, so nothing held it and the authorization callback never fired. Verified on the simulator with the permission reset first. The other two `NSObject` delegates in `iosMain` (`LocationProvider`, `FilePicker`) were audited and already hold strong references.
- **Unused illustrations removed** (2026-08-04) — 16 of the 24 Dotion illustrations were referenced by nothing; 32 files with their dark variants are gone. Repo hygiene rather than a size win: the AAB moved ~5 KB, because R8 resource shrinking was already excluding them. `scripts/install_illustrations.py` still lists the full pack, so re-running it restores all 24.

## ⚠️ Blockers that need YOU (credentials / accounts)

1. **Android release signing.** The AAB builds **unsigned** until the keystore properties are
   set. The keystore is at `~/Documents/tribe/lifeplanner.jks` and does **not** need copying
   into the repo. Either add to `local.properties` (git-ignored):
   ```
   RELEASE_STORE_FILE=~/Documents/tribe/lifeplanner.jks
   RELEASE_STORE_PASSWORD=…
   RELEASE_KEY_ALIAS=…
   RELEASE_KEY_PASSWORD=…
   ```
   …or pass them per-build without persisting them, since `localProp()` falls back to Gradle
   properties:
   ```
   ./gradlew :app:androidApp:bundleRelease \
     -PRELEASE_STORE_FILE=~/Documents/tribe/lifeplanner.jks \
     -PRELEASE_STORE_PASSWORD=… -PRELEASE_KEY_ALIAS=… -PRELEASE_KEY_PASSWORD=…
   ```
   Verify before uploading — an unsigned AAB still builds green:
   ```
   unzip -l app/androidApp/build/outputs/bundle/release/androidApp-release.aab | grep -E 'META-INF/.*\.(RSA|DSA|EC)'
   ```

2. **GitHub Actions secrets** (only if you ship via CI — see the CI section). Needed:
   `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `POSTHOG_API_KEY`, `PERSONA_API_SECRET`,
   `GOOGLE_SERVICES_JSON`, `GOOGLE_SERVICE_INFO_PLIST`, `RELEASE_KEYSTORE_BASE64`
   (`base64 -i ~/Documents/tribe/lifeplanner.jks | pbcopy`), `RELEASE_STORE_PASSWORD`,
   `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.


3. ~~**The release lives on a feature branch.**~~ **RESOLVED 2026-08-20.** The v3 work is
   merged: `main` is at `843856a` (merge of `com3run/learning-in-the-present`, PR #23) and
   carries everything in this runbook. Tag from `main`.

## Pre-submit checks (against the consoles)
- Android **versionCode 11 > last published** code.
- iOS **build 8 > last uploaded** build. v3 has changed a lot since build 8 was chosen; if
  anything was already uploaded as 8, bump `CURRENT_PROJECT_VERSION`.
- **Data safety (Play) / privacy (App Store)** declare health data + AI usage.
- Play requires target API 36 by 31 Aug 2026 — already set (`android-targetSdk = 36`).

## CI

All three workflows are **manual** (`workflow_dispatch`); the old `develop` trigger was dead.

Until 2026-08-03 none of them could produce a shippable artifact: `local.properties`,
`google-services.json` and `GoogleService-Info.plist` are all git-ignored, so CI built the app
with an **empty `SUPABASE_URL`** (no auth, sync, AI or Learn content) and an **unsigned** AAB,
and the iOS job was pinned to a `Xcode_15.0.app` that no longer exists on the runner image.
That is fixed:

- `.github/actions/restore-android-secrets` recreates the three files from secrets, shared by
  `android.yml` and `manual_deploy_to_firebase.yml`.
- `android.yml` fails the job if the AAB comes out unsigned, and deletes the secrets afterwards.
- `ios.yml` restores `local.properties` + the Firebase plist and tracks `latest-stable` Xcode.
- Both now run Java 21 (matching local) on `checkout@v4` / `setup-java@v4` / `setup-gradle@v4`.

CI still needs the secrets from blocker 2 before it will run.

## Analytics / force-update

`POSTHOG_API_KEY` was **missing** from `local.properties`, which silently disables PostHog —
and with it `ForceUpdateChecker`, which reads the `force_update_min_version` flag. It is now set.

`BuildKonfig.APP_VERSION` was hardcoded to `"2.5"` while the app shipped 3.0.0. That value is
what `ForceUpdateChecker` compares against the flag, and what Settings displays. The
`force_update_min_version` flag is live at 100% rollout with
`{"min_version": "2.2", "mode": "soft"}` — harmless at 2.5, but raising it to `3.0.0` after
launch would have shown **every v3 user** a blocking update wall pointing at a store listing
they were already current on. `APP_VERSION` now derives from the version catalog.

Post-launch, once v3 adoption looks healthy, bump the flag payload to
`{"min_version": "3.0.0", "mode": "soft"}` to pull the remaining 2.3 users up.

## Not in v3 (deliberately)
- **Billing / paywall.** `DefaultPremiumGate.isPremium` is hardcoded `true`, so every feature
  is open. The RevenueCat work sits unmerged on `com3run/tri-70-revenuecat-billing`
  (`REVENUECAT_*` keys are already in `local.properties`). v3 ships free.
- **Community journals.** Backend (`supabase/community_journals.sql`, `share-journal`,
  `report-content`) is written but not applied or deployed, and there is no client UI or
  feature flag. See `docs/SPEC-community-journals.md`.
- ~~**Wheel of Life.**~~ **This shipped after all.** The ten-area self-assessment merged via
  PRs #20 and #21, so it IS in this build, along with the Learn hub rework from PR #23. Its
  Supabase tables (`wheel_scores`, `wheel_snapshots`, both with RLS, both in
  `cleanup_tombstones`) went live on `rkdggdfabwgukspylybu` on 2026-08-04 and are now actually
  read and written. It carried schema v40 and v41; v42 landed on top.

## Store copy
`../lifeplanner-assets/docs/store/whatsnew-3.0.0.md` (Play ≤500 chars + Apple release notes).

## Ship
- **Android:** signed AAB → Play Console (internal testing → production), or run the
  `Android CI` workflow for Firebase App Distribution.
- **iOS:** Xcode archive scheme `iosApp` → TestFlight → App Store.
- **Backend:** already live. No action.

## Post-release
- Watch the Telegram monitoring alerts (health every 5 min, store-watch every 30 min).
- `store-watch` will alert when the new version goes live on each store.
