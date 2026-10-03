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

## The paywall decision (blocks step 4, yours to make)

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
- [ ] Push `com3run/v4-life-planner` to GitHub. 52 commits live only on this Mac.
- [ ] Android core-loop pass on a safe device, screenshot each step.
- [ ] One cloud sync round trip with a real test account (tables are live, never watched).

### Phase 1: the build is releasable
- [ ] Paywall decision above. If A: delete `DefaultPremiumGate`'s dead branch and the Causal gate.
- [ ] Merge PR #29 (signing guard) so a release AAB cannot come out unsigned.
- [ ] Triage PR #25 (decision journal) and PR #24 (architecture) : merge or close, do not leave rotting.
- [ ] Bump `app-versionName` to 4.0.0, `app-versionCode` to 12 in gradle/libs.versions.toml.

### Phase 2: store paperwork (needs the transfer done)
- [ ] Confirm the transfer to store@tribe.az completed and the app is editable there.
- [ ] Update the Play **health declaration**: Exercise read/write, Nutrition + Hydration write,
      WRITE_MINDFULNESS. Sleep/Weight read-only. Without this the review bounces.
- [ ] Refresh store listing: screenshots of the v4 shell, description, what's-new for 4.0.
- [ ] Data safety form review (new areas collect more; keep it honest).

### Phase 3: signed build and internal test
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
