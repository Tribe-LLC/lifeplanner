# v4 plan

Written 2026-10-01. The working plan for getting v4 (the life-areas planner) from a local branch
to the store. Read this first after a context reset.

## Where things stand

| | |
|---|---|
| branch | `com3run/v4-life-planner`, worktree `~/Projects/LifePlanner/code/worktrees/lifeplanner-v4` |
| size | 52 commits, 247+ files, ~+34,600 / −308, built 28-29 Sep |
| pushed? | **No. These commits exist only on this Mac.** |
| `origin/main` | `8d4ed60`, 22 Sep. Has not moved since. |
| store | still **2.3**, from 2 April 2026. Nothing from v3 or v4 has ever shipped. |
| version in `libs.versions.toml` | still `3.0.0` / code `11` |
| tests | 1441 passing as of 29 Sep |

v4 is **additive and already flag-gated**: `FeatureFlags.V4_SHELL = true` switches `App.kt` to
`V4AppRoot` (`AppNavV4.kt`), and every v3 screen stays reachable as a route. Only 308 deletions
across the whole branch. Nothing has been thrown away, so the decision about retiring v3 is still
open and still cheap.

Areas built: Today, Life, Coach, Money, Fitness, Travel, Meals, Study, Habits, Mind, Career,
Plans, plus the today's-habits widget and iOS reminder actions.

## Already verified, do not redo

- **Migrations are safe.** Schema 43, the Android hand-written chain in `DatabaseMigrations.kt`
  runs to `migrateToVersion43`, and both guards pass on v4: `DatabaseMigrationsTest` (the Android
  chain reaches the same schema as a fresh install) and `SqmMigrationTest` (the iOS `.sqm` route
  agrees). An existing 2.3 or 3.0.0 user keeps their data.
  Run them with `--no-build-cache`. The build cache fails packing
  `res/raw/ambient_fireplace.wav`, which looks like a test failure and is not.
- **The "Check In Habits" crash is fixed** (`8087337`, 2026-10-01). Tapping that launcher shortcut
  with the app already running killed the process (`journal_habits` is not a nav destination, and
  only the cold-start path translated it). Cherry-picked from `com3run/deeplink-habits-crash`,
  plus the second call site in `AppNavV4.kt:231`. Compiles.
  Note: Codex also works on `com3run/deeplink-habits-crash` in the main checkout.
- v4 has been run on Android (`LP_V4_Dev`) and on iOS sim F403CB23 through 29 Sep.

## Open before release

Carried from the 29 Sep session plus what this session found.

1. **Push the branch.** 52 commits on one disk is the largest risk here and the cheapest to retire.
2. **Restore `POSTHOG_API_KEY` in this worktree's `local.properties`.** It is deliberately blank so
   test builds do not pollute project 293959. A release build from here would ship with no
   analytics at all. The main checkout has the real key.
3. **Play Console health declaration.** Must add Exercise read/write, Nutrition + Hydration write
   and WRITE_MINDFULNESS before release. Sleep/Weight are now honestly marked read-only.
4. **Sync round trip with a real test account.** `V4_CLOUD_SYNC = true` and the four v4 tables are
   live in production Supabase with RLS, but the round trip has never been watched: the `LP_V4_Dev`
   guest does not sync.
5. **In-place upgrade, both platforms.** Install the previous build, install v4 over it, confirm
   the data is still there. The tests predict this; nobody has watched it happen.
6. **Decide `PremiumGate`.** `core/PremiumGate.kt:14` still reads
   `// TODO(billing): wire to real entitlement. Open to everyone until then.` Either ship v4 open
   deliberately, or land PR #5 (RevenueCat) first. Shipping it open by accident is the bad outcome.
7. **Land PR #29 (signing guard) before any release build.** Without it a release AAB builds
   unsigned and silently, which has already cost an afternoon once.
8. **Bump the version**: `app-versionName` to `4.0.0`, `app-versionCode` to `12`.
9. **Confirm Crashlytics receives from the release build.** PostHog has `$exception` capture off,
   so Crashlytics is the only crash signal that exists.

Known rough edges, deliberate for now: routines outlive a deleted plan; Health distance is not
imported so run plans fall back to minutes; the coach's step suggestion has never been called live;
the workout swap nudge is untested on a device (needs real sleep data); undoing a workout does not
delete it from Health.

## The four stale PRs

All predate v4, none merged, `origin/main` has not moved since 22 Sep.

- **#29** signing guard. Land it (see above).
- **#25** decision journal, gamified. Ready, just stale.
- **#24** Android/KMP architecture conformance. Will conflict hard with 247 changed files. Decide
  keep or close, do not let it rot.
- **#5** RevenueCat billing. Tied to the `PremiumGate` decision.

## Standing rules

- Safe Android device is AVD **`LP_V4_Dev`** (guest account, safe to tap). Check
  `adb emu avd name` before touching any emulator. `LP_SmokeTest` took port 5554 once already.
- Safe iOS device is sim **F403CB23** (fresh iPhone 17, guest). Sim **E4D88A3E is his real
  account**: view only, never tap anything that writes.
- The physical iPhone 14 Pro build is blocked on signing. Xcode has No Accounts; Kamran must add
  his Apple ID (team 9T27H6JKJM) himself.
- Auto-commit small verified fixes locally. Do not push without being asked.
- Release signing needs `lifeplanner.jks` plus the `RELEASE_*` properties, which Kamran sets
  himself (keystore in `~/Projects/LifePlanner/keys`).
- Play requires targetSdk 36 from 31 Aug 2026. v4 is on compileSdk 37 / targetSdk 36 already.

---

# Release plan (2026-10-03)

Context: the Play listing is mid-transfer to the org account **store@tribe.az**. Publishing
is paused until Google completes the transfer. The live store app is still **2.3** (2 April),
so the first v4 update is a 2.3 -> 4.0 jump for almost every existing user. That upgrade path
is migration-verified. v4 is also installed on the S24 (in-place over the sideloaded 3.0.0,
data kept) as of today.

## The paywall decision: DECIDED 2026-10-05, RevenueCat (path B)

There is no paywall and no billing SDK. Three ways to go:

- **A. Ship free.** Fastest. Remove the dead gate, publish 4.0 free, add billing later as 4.1.
  Lowest risk, no revenue day one.
- **B. Freemium with RevenueCat.** Land PR #5, pick what is free vs paid (candidate: the Coach,
  or the heavier areas like Travel/Study, or Causal Insights), design a paywall screen, wire
  products in Play. Adds roughly 3-5 days and needs RevenueCat + Play product setup.
- **C. Free trial then subscription.** Same wiring as B plus a trial. Most revenue, most work.

Recommendation: **A now, B as 4.1.** Get v4 in front of users on the new account, learn from the
funnel, then charge once you know which area people actually keep using.

## Phases

### Phase 0: lock in what exists (safe, no decision)
- [x] Push `com3run/v4-life-planner` to GitHub (2026-10-05). On origin now.
- [x] Android core-loop pass on LP_V4_Dev (2026-10-05): Today, Life (dashboard + Career area + month heatmap), Coach (live AI replies + follow-up chips), and a habit check-in (Drink water 0->1 of 8) all work, zero crashes, clean migration.
- [ ] One cloud sync round trip with a real test account (tables are live, never watched). Still open: the LP_V4_Dev guest does not sync, so this needs a signed-in test account.

### Phase 1: the build is releasable
- [x] Paywall: RevenueCat chosen (2026-10-05). SDK purchases-kmp 3.11.0 wired on both platforms, gate backed by the `premium` entitlement, Plus paywall + Customer Center screens, a Plus row on You. See "RevenueCat setup" below for the dashboard side. **Still open: which features Plus actually locks** (nothing is locked yet).
- [x] PR #29 (signing guard) merged to main (ce7ec1a) and on v4; main merged into v4 (26432f2).
- [x] PR #25 and #24 closed 2026-10-05 with reasons, branches kept. #25: v4 has no entry point to the decision journal. #24: 208-file v3 refactor overlapping v4's core files. PR #5 superseded by the v4 RevenueCat port.
- [x] Version bumped to 4.0.0 / 12 (47cf4f8), confirmed in the built APK.

### Phase 2: store paperwork (needs the transfer done)
- [ ] Confirm the transfer to store@tribe.az completed and the app is editable there.
- [ ] Update the Play **health declaration**: Exercise read/write, Nutrition + Hydration write,
      WRITE_MINDFULNESS. Sleep/Weight read-only. Without this the review bounces.
- [ ] Refresh store listing: screenshots of the v4 shell, description, what's-new for 4.0.
- [ ] Data safety form review (new areas collect more; keep it honest).

### Phase 3: signed build and internal test
- [ ] Fill local.properties in the v4 worktree before the release build: RELEASE_* (Kamran),
      REVENUECAT_ANDROID_API_KEY / REVENUECAT_IOS_API_KEY, and **restore POSTHOG_API_KEY** (blank on
      purpose in this worktree; the main checkout has the real one). A release without it ships blind.
- [ ] Signed AAB (Kamran enters the keystore passwords; keystore in ~/Projects/LifePlanner/keys).
      NOTE: a transferred app keeps Google Play App Signing, so the upload key is unchanged.
- [ ] Confirm Crashlytics receives from the release build (PostHog $exception is off).
- [ ] Upload to the internal track, install from Play on the S24, verify the real 2.3/3.0 -> 4.0
      upgrade one more time from a store build, not a sideload.

### Phase 4: ship
- [ ] Internal -> closed/open test -> staged production rollout.
- [ ] Watch the funnel on PostHog 293959 for the first cohort.

## Rough effort
Path A: about 2-3 focused days of my work plus your paywall call, the health declaration, and the
keystore passwords. Path B adds 3-5 days for billing. The transfer completing is the only hard
external dependency.


---

## RevenueCat setup (dashboard side, Kamran)

Code side is done. With no keys in local.properties billing is OFF: the gate stays open and the Plus
row on You hides itself, so a build without keys behaves exactly like a free app. Turning billing on
is all dashboard and store work, in this order:

1. RevenueCat: create the project, add the Android app (package `az.tribe.lifeplanner`) and the iOS app
   (bundle id from Xcode). Free tier: no cost until $2.5k/month tracked revenue.
2. Play Console (after the transfer to store@tribe.az completes): create the subscription products
   (e.g. monthly + yearly). Products can only go Active once an AAB with billing has been uploaded to a
   track, the internal track is enough.
3. Play: create a service account with financial access and upload its JSON to RevenueCat, so
   RevenueCat can validate purchases. App Store Connect: the in-app purchase key, same idea.
4. RevenueCat: create the entitlement with identifier exactly `premium` (the code checks
   `RevenueCatPremiumGate.PREMIUM_ENTITLEMENT`), attach the products, build an Offering, and design
   the Paywall there. The app renders whatever paywall the dashboard holds, so copy and prices change
   without a release.
5. Put the public SDK keys (`goog_...`, `appl_...`) into local.properties as
   `REVENUECAT_ANDROID_API_KEY` / `REVENUECAT_IOS_API_KEY`.
6. Test with a licence tester on the internal track. Sandbox purchases are free.

Decision still open: **what Plus locks.** Natural candidates, in order of how defensible they are:
the Coach (real AI cost per message, so a daily free allowance with unlimited on Plus), the
cross-area insights on Life, or the heavier areas (Travel, Study). Nothing is locked today; locking
a feature is one `premiumGate.isPremium()` check plus a link to `V4Routes.PLUS`.

## Play health declaration answers (Phase 2)

Every permission below is requested in the manifest AND used in code (8 record types referenced in
HealthDataManager.android.kt, 4 write call sites), so the declaration is honest.

| Permission | Why the app needs it |
|---|---|
| READ_STEPS | Fitness page shows daily steps; habits can tick themselves from activity |
| READ_EXERCISE | Fitness page lists workouts from other apps; plans track progress from them |
| WRITE_EXERCISE | Workouts logged in LifePlanner are saved to Health Connect |
| READ_HEART_RATE | Shown with workouts on the Fitness page |
| READ_SLEEP | Sleep and mind: sleep debt and the coach's workout swap after a short night |
| READ_WEIGHT | Shown on the Fitness page (read only) |
| WRITE_NUTRITION | Meals logged in the Meals area are saved to Health Connect |
| WRITE_HYDRATION | The water habit saves each glass to Health Connect |
| WRITE_MINDFULNESS | Breathing sessions in Sleep and mind are saved as mindful minutes |

Also sensitive, for the Data safety form: calendar read/write (interviews and study blocks go to the
calendar), coarse location (weather on Today and trip forecasts).

## What's new in 4.0 (Play release notes, 497/500 chars)

```
LifePlanner 4 is built around your whole life.
- Pick your areas: habits, fitness, money, meals, study, travel, career, mind.
- Today shows what to do now and carries over what you missed, no guilt.
- Add anything in one line: "lunch ramen 12.50" lands in Meals and Money.
- Plans whose steps date themselves and track progress from your logs.
- A coach that sees your week and suggests the next step.
- Health Connect both ways, and your calendar.
Everything from earlier versions comes with you.
```
