# Backend migration: Supabase to Cloudflare (LifePlanner 4.1)

Written 2026-10-09 (Phase 0: inventory and plan, no code yet). Kamran's ask: "i really want to move from
supabase it is not convenient".

**Ground rules**

- 4.0.0 (versionCode 12) ships on Supabase and is on the Play internal track. Nothing here touches it.
- This is the **4.1** release. Old app versions keep using Supabase for months. Nothing in production
  Supabase is deleted or altered; we only read from it (additive reads).
- Local commits only. No push until Kamran says so.

## 1. Inventory of what the app uses from Supabase

Project `rkdggdfabwgukspylybu` (org Tribe, free plan). Database 78 MB. Counted live on 2026-10-09.

### 1.1 Auth (GoTrue)

| Fact | Value |
|---|---|
| Users in `auth.users` | **436** (not 118; 118 is the number of rows in `public.users`, i.e. people who got through onboarding) |
| Anonymous guests | 404 (93%) |
| Email + password | 36 (32 `email` provider, 24 with confirmed email) |
| Password hashes | all bcrypt (`$2a$`) |
| Google | provider is off; `AuthService.signInWithGoogle()` is a stub returning an error |
| Methods used by the client | email+password sign in/up, anonymous, link email to anonymous account (`updateUser`), magic link / 6-digit OTP (`signInWith(OTP)`, `verifyEmailOtp` for MAGIC_LINK and SIGNUP), password reset email, resend verification, session auto-refresh and persistence |
| Deep links | `lifeplanner://auth` scheme plus `https://tribe.az/lifeplanner/auth/callback` (served by the `auth-redirect` function), iOS `DeepLinkHandler` feeds URLs to `handleDeeplinks` |

### 1.2 Database (Postgres, RLS on everything)

45 tables in `public`. Pattern: nearly every user table has `user_id uuid references auth.users on delete cascade`,
`updated_at`, `is_deleted`, `sync_version`, and RLS policies `select/insert/update/delete` all reduced to
`user_id = auth.uid()`. So authorization is one rule, which makes moving it into Worker code cheap and safe.

**User-owned, synced (32 tables, all `user_id` scoped, soft delete, tombstone cleanup after 30 days):**
`users`, `goals`, `milestones`, `goal_history`, `goal_dependencies`, `user_progress`, `habits`,
`habit_check_ins`, `journal_entries`, `badges`, `challenges`, `chat_sessions`, `chat_messages`,
`review_reports`, `reminders`, `custom_coaches`, `coach_groups`, `coach_group_members`, `focus_sessions`,
`coach_persona_overrides`, `user_situations`, `life_values`, `decisions`, `identity_statements`,
`decision_profiles`, `knowledge_reads`, `wheel_scores`, `wheel_snapshots`, `life_logs`, `budgets`, `trips`,
`trip_items`. (The 45 table total also includes the v4 sync tables; `V4SyncSchemaTest` checks their columns
against `supabase/schema.sql`. Task 1 of Phase 1 is to dump the exact final list from the live DB.)

**Foreign keys between app tables (must be created in dependency order, and the Worker must tolerate
child-before-parent arrival during sync, as it does today):**
`milestones.goal_id -> goals`, `habits.linked_goal_id -> goals`, `habit_check_ins.habit_id -> habits`,
`journal_entries.linked_goal_id/linked_habit_id`, `reminders.linked_goal_id/linked_habit_id`,
`focus_sessions.goal_id/milestone_id`, `goal_dependencies.source/target_goal_id -> goals`,
`chat_messages.session_id -> chat_sessions`, `coach_group_members.group_id -> coach_groups`.
In D1 I will keep these as plain TEXT columns with indexes and **drop the hard FK constraints** for the
synced tables, because the syncers push table by table and a Postgres FK already forced ordering in
`SyncManager`; in SQLite with `PRAGMA foreign_keys` on, a deferred ordering mistake becomes a hard 500.
Referential integrity for these is client-owned anyway.

**Server-only or read-mostly tables:**

| Table | Access | Notes |
|---|---|---|
| `ai_usage_logs` | user insert/select | written by ai-proxy, read by `AiUsageRepositoryImpl` |
| `gamification_log` | user insert/select | written by triggers |
| `content_embeddings` | user CRUD | **0 rows**, pgvector, `match_user_content()` RPC. Drop for now, revisit with Vectorize if RAG ever ships |
| `scheduled_posts` | user CRUD + service role | marketing scheduler, `media_urls text[]`; NO ACTION FK (blocks user delete, delete-account clears it first) |
| `beginner_objectives`, `user_situations`, `cached_personas` | user CRUD | |
| `builtin_coaches`, `system_prompts`, `knowledge_lessons`, `knowledge_collections`, `personas_cache` | public read, admin write | content fetched by `BuiltinCoachFetcher`, `SystemPromptFetcher`, `KnowledgeFetcher`, `PersonaApiFetcher` |
| `blog_posts`, `stories`, `coach_posts` | public read published | `CoachPostRepositoryImpl`, `StoryRepositoryImpl` |
| `store_versions` | service role only | store-watch state |

Odd types to convert for SQLite: `jsonb` (`specialties`, `metadata`, `characteristics`, `tags`, `priorities`,
`scores`, `*_json`) becomes TEXT holding JSON; `text[]` (`blog_posts.tags`, `scheduled_posts.media_urls`)
becomes TEXT JSON; `vector` is dropped; `uuid` and `timestamptz` become TEXT; `boolean` becomes INTEGER 0/1.

### 1.3 Server-side logic in Postgres (this is the biggest hidden item)

- `update_sync_metadata()` BEFORE UPDATE triggers on all 32 synced tables:
  `updated_at = now(); sync_version = old.sync_version + 1`. **Server clock is the sync clock.** The client
  pulls `updated_at > lastPull` and compares ISO strings, so the Worker must stamp `updated_at` in one fixed
  ISO-8601 UTC format on every write.
- Gamification triggers: `trg_goal_created/completed`, `trg_milestone_completed`, `trg_habit_checkin`,
  `trg_journal_entry`, `trg_focus_session` (insert + update), `trg_beginner_objective_completed` (insert +
  update), calling `award_xp()`, `check_and_award_badges()`, `update_challenge_progress()`,
  `calculate_level_from_xp()`, writing `gamification_log`, `user_progress`, `badges`, `challenges`.
- RPC `update_daily_streak` called by `GamificationRepositoryImpl`.
- `cleanup_tombstones()` (30 day purge of soft-deleted rows).
- `match_user_content()` pgvector similarity (unused, 0 rows).
- `rls_auto_enable()` helper.

SQLite supports triggers, so the gamification triggers can be ported nearly 1:1 as D1 triggers (plpgsql to
SQLite SQL, `INSERT ... ON CONFLICT DO UPDATE`). I will port them as triggers rather than Worker code so
behaviour and idempotency stay identical. Needs a careful parity test (see Phase 2).

### 1.4 Storage

One public bucket `assets`, **11 objects** (built-in coach images). Hardcoded absolute URLs in
`BuiltinCoachFetcher.kt:15`. A second Supabase project (`jimiwruckhlxkmnduhrn`, persona-api and its own
`assets` bucket, used by `PersonaApiFetcher.kt`) is an **unrelated project** and out of scope for this move;
note it so nobody thinks it was forgotten.

### 1.5 Edge functions (10, Deno, 3,046 lines total)

| Function | Lines | Does | External | Target |
|---|---|---|---|---|
| `ai-proxy` | 1110 | JWT-verified LLM proxy, providers OpenAI / Gemini / Grok, SSE streaming, usage logging, embeddings (Gemini text-embedding-004) | OpenAI, xAI, Google | Worker route, secrets, `TransformStream` passthrough |
| `delete-account` | 41 | Deletes caller (clears NO ACTION tables, then user; cascade does the rest) | | Worker route, one D1 batch |
| `auth-redirect` | 185 | Landing page that bounces email links into `lifeplanner://` | | Static route in the Worker |
| `resend-verification` | 66 | Resend signup email using service role | Supabase email | Worker route + email provider |
| `share-journal` | 133 | Moderation via OpenAI `/moderations`, publish to community | OpenAI | Worker route |
| `report-content` | 74 | Content reports | | Worker route |
| `mcp-server` | 938 | MCP server over Supabase data (user JWT scoped) | | Port last, optional; see risks |
| `persona-sync-webhook` | 149 | Receives persona updates, writes `personas_cache` (service role) | webhook secret | Worker route |
| `health` | 157 | Pings all functions, Telegram alert | Telegram | Cron Trigger |
| `store-watch` | 193 | Polls App Store / Play for version, Telegram alert, writes `store_versions` | iTunes lookup, Play page | Cron Trigger |

Secrets in use (never copy values into the repo): `OPENAI_API_KEY`, `GEMINI_API_KEY`, `GROK_API_KEY`,
`TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `HEALTH_KEY`, `PERSONA_WEBHOOK_SECRET`, plus Supabase's own.

### 1.6 Scheduled jobs

`pg_cron`: `lifeplanner-health` every 5 min, `lifeplanner-store-watch` every 30 min (both `pg_net` HTTP POST
to the function). No tombstone-cleanup cron is scheduled today (the function exists, nothing calls it).

### 1.7 Where the KMP client touches Supabase

Android and iOS share all of it (`app/shared/src/commonMain`), so one change covers both platforms. Only
engine and deep link glue is platform specific.

| Area | Files | Surface |
|---|---|---|
| Client + DI | `di/supabaseModule.kt`, `di/Koin.kt`, `di/appModule*.kt`, `iosMain/.../supabaseEngine.ios.kt`, BuildKonfig `SUPABASE_URL` / `SUPABASE_ANON_KEY` (`app/shared/build.gradle.kts:233-239`), `libs.versions.toml` (supabase 3.1.1: postgrest, auth, compose-auth) | `SupabaseClient` injected into ~40 classes |
| Auth | `data/auth/AuthService.kt` (390 lines, interface + `SupabaseAuthService`), `ui/viewmodel/AuthViewModel.kt`, `AuthViewModelActions.kt`, iOS `DeepLinkHandler.kt`, `App.kt` | `sessionStatus` Flow, `currentUserOrNull`, `refreshCurrentSession`, `updateUser`, `handleDeeplinks`. **`AuthViewModel` and `SyncManager` reach past `AuthService` straight into `SupabaseClient.auth`**; that leak must be closed first |
| Sync | `data/sync/TableSyncer.kt`, `SyncManager.kt` (446), 33 syncers in `data/sync/syncers/`, `dto/SyncDtos.kt` | only two Postgrest patterns: `postgrest[t].upsert(dtos)` and `select { filter { eq("user_id"); gt("updated_at", lastPull) } }`. Per-table timestamps for LWW |
| Read-only content | `KnowledgeFetcher`, `BuiltinCoachFetcher`, `SystemPromptFetcher`, `CoachPostRepositoryImpl`, `StoryRepositoryImpl`, `AiUsageRepositoryImpl` | `select` with `order` and filters |
| Gamification | `GamificationRepositoryImpl.kt:181` | `rpc("update_daily_streak")` |
| AI | `data/network/AiProxyServiceImpl.kt` (261), `AiErrors.kt` | Ktor to `/functions/v1/ai-proxy` with `bearerAuth` + `apikey` header, SSE parse |
| Delete | `AuthService.deleteAccount()` | Ktor to `/functions/v1/delete-account` |
| Tests | `V4SyncSchemaTest`, `KnowledgeSeedGeneratorTest`, plus syncer and viewmodel tests using the no-Supabase test constructors | |

Good news: the app is offline-first with its own SQLDelight DB as source of truth, sync is a thin LWW layer,
and the two sync query shapes are tiny. That is why a rewrite of the transport is much cheaper than the
33-syncer file count suggests.

## 2. Target architecture (and where I disagree with the proposal)

Agreed as proposed: **one Worker (Hono) + D1 + Better Auth on D1 + streaming ai-proxy + Cron Triggers.**
Deviations and decisions:

1. **Do not reimplement PostgREST.** The Worker exposes a small typed sync API, not a generic SQL surface:
   `GET /v1/sync/:table?since=ISO` and `POST /v1/sync/:table` (array upsert, LWW). A per-table registry in
   the Worker (`tables.ts`: columns, types, bool/json columns, owner column) drives validation, bool/JSON
   conversion and the ownership filter. Public content tables get `GET /v1/content/:table`. The client
   syncers change one line each (`api.upsert`, `api.pull`).
2. **D1 gotchas designed in:** 100 bound-parameter limit per statement (a 24 column row means 4 rows per
   multi-row insert), so upserts go as `db.batch([...single-row upserts])`; no FK enforcement on synced
   tables (see 1.2); all timestamps TEXT ISO UTC stamped by the Worker; booleans as 0/1 converted at the
   edge. Total data is 78 MB against a 10 GB D1 limit and 5M free reads per day, so no sharding or Hyperdrive.
3. **Better Auth: yes, with `anonymous`, `emailOTP`, `bearer` plugins, email+password, Google via `idToken`.**
   No Kotlin SDK exists, so the client side is a hand-written ~300 line `CloudflareAuthService` over Ktor,
   which is the same size as today's `SupabaseAuthService`. Sessions are opaque bearer tokens with sliding
   expiry (no JWT refresh dance); use KV as Better Auth `secondaryStorage` to keep session lookups off D1.
   Fallback if Better Auth fights D1/Workers in the spike: `hono/jwt` + a 400 line hand-rolled auth. The
   `AuthService` interface hides the choice either way.
4. **Anonymous to email linking must keep the same user id** (Supabase `updateUser` does; all rows are keyed
   by it). Better Auth's anonymous plugin by default creates a new user and fires `onLinkAccount`. I will
   implement `onLinkAccount` as `UPDATE ... SET user_id` across tables inside one `batch`, **or** add a
   custom `POST /auth/link` that upgrades the existing anon user in place (preferred, one UPDATE, no row
   moves). Spike this first; it is the single riskiest auth behaviour.
5. **Password hashing and the free Workers CPU limit.** Free Workers allow 10 ms CPU per request. bcrypt
   verify (legacy, 36 users) and any slow hash exceed that. Options: (a) Workers Paid, USD 5/month, 30 s CPU
   (also lifts the 100k requests/day cap); (b) stay free: legacy users reset via email OTP (36 people), new
   hashes via Web Crypto PBKDF2 which runs native. **Recommendation: take (a) when 4.1 goes to staging**;
   Kamran may already be on Paid (8 Workers, 5 D1), check before treating this as a new cost. Everything
   else (D1, KV, R2 10 GB, Cron Triggers, Worker streaming) fits free tiers at this scale.
6. **Silent migration of existing sessions instead of "guests re-create".** 404 of 436 users are guests; making
   them recreate loses nothing locally but orphans their server rows and breaks identity. Better: on first 4.1
   launch the client reads its persisted Supabase session, refreshes it over plain HTTP, and calls
   `POST /v1/auth/migrate` with the Supabase access token. The Worker verifies it by calling Supabase
   `GET /auth/v1/user` (read-only, no JWT secret needed), and issues a Cloudflare session **for the same user
   id**. Guests, password users and OTP users all land on their own data with zero prompts. If the old token
   is dead, guests fall back to a fresh guest (re-upload local data, as originally planned) and password users
   sign in with their password (legacy bcrypt verify, then rehash on first login).
7. **Server-clock LWW is preserved.** Worker stamps `updated_at` and bumps `sync_version` on every write,
   exactly like `update_sync_metadata()`. Clients need no logic change.
8. **Email delivery:** Better Auth OTP and signup mails need a sender. Recommend Resend free tier (3,000/mo)
   through its HTTP API, unless Cloudflare Email Service is already enabled on his zone (then use that to
   stay on one vendor). Decide in Phase 3 based on what Kamran has; not blocking earlier phases.
9. **Gamification as D1 triggers**, not Worker code (see 1.3).
10. **Storage:** 11 coach images to an R2 bucket with a public custom domain. Update the one hardcoded base URL.
11. **mcp-server (938 lines)**: not user-visible in the app; port it last or leave it on Supabase and cut it
    when Supabase is finally switched off. Ask Kamran if anything depends on it (I do not know of a client).
12. **API home:** `lp-api.tribe.az` (zone in Kamran's Cloudflare; DNS I can create with the existing DNS token
    once he confirms the name), Worker `lifeplanner-api`, D1 `lifeplanner-prod` plus `lifeplanner-staging`, KV
    `lp-sessions`, R2 `lp-assets`. Repo: new directory `cloudflare/` inside this repo (Worker, migrations,
    scripts) so schema, client and server version together.

## 3. Migration approach (data and users)

1. **Schema generation by script, not by hand.** A script reads `information_schema` + `schema.sql` and emits
   D1 migrations (`cloudflare/migrations/0001_init.sql`), applying the type map in 1.2. Review diffs once.
2. **Data copy:** read-only export from Supabase (Management API / `execute_sql` selects, paged), transform,
   `wrangler d1 execute --file` in chunks into staging first. Identical primary keys and `user_id`s. Auth tables:
   `auth.users` (id, email, `is_anonymous`, `email_confirmed_at`, `raw_user_meta_data.display_name`,
   `created_at`, bcrypt hash) into Better Auth `user` / `account` tables. Hashes copied verbatim into the
   credential account with a `legacy_bcrypt` marker; a custom `password.verify` checks bcrypt (`bcryptjs`) when
   the marker is set and the Worker rehashes on first success.
3. **Idempotent and repeatable:** the copy script upserts by id and takes a `--since` argument (`updated_at`).
   Run it several times before cutover; run a final delta right before the 4.1 rollout.
4. **Two-way safety net during the transition:** 4.0 clients keep writing to Supabase. Until they age out, a
   scheduled **bridge job** (Cron Trigger, read-only against Supabase) pulls `updated_at > cursor` from
   Supabase into D1 so a user on 4.0 on one phone and 4.1 on another does not diverge by much. Switch the
   bridge off when 4.0 active users hit near zero. 4.1 clients also do a **one-time full re-push** of local
   data after migrate (all rows marked unsynced), which makes the copy a warm start rather than a
   point-in-time requirement.
5. **Supabase stays alive for months**, unchanged. Free-plan caveat: Supabase pauses free projects after
   7 days of inactivity; the 5 minute health cron plus 4.0 traffic prevent that today. Do not remove the
   health cron on Supabase until the final shutdown decision.
6. **Verification:** row counts per table and per user, max(`updated_at`) per table, hash spot check on a test
   account created for this purpose, plus a read-only check that no Supabase row changed because of us.

## 4. Client plan (Android and iOS together, shared code)

Order matters, each step compiles and passes tests before the next:

1. **Close the abstraction leak.** `AuthViewModel`, `AuthViewModelActions`, `SyncManager`, `GamificationRepositoryImpl`
   touch `SupabaseClient.auth` directly. Add to `AuthService`: `sessionState: Flow<SessionState>`,
   `currentSession()`, `accessToken()`, `refresh()`, `updateDisplayName()`, `handleDeepLink(url)`. Behaviour
   unchanged on Supabase (this step can ship inside 4.0.x if wanted, but I will not touch 4.0).
2. **Introduce `BackendApi`** (Ktor client, base URL from BuildKonfig `API_BASE_URL`, bearer token from
   `AuthService`): `pull(table, since)`, `upsert(table, dtos)`, `content(table, order)`, `rpcDailyStreak()`,
   `deleteAccount()`. Syncers swap `supabase.postgrest[...]` for it (mechanical, 33 files, DTOs untouched).
3. **`CloudflareAuthService`** implementing the new interface, plus session storage in multiplatform-settings
   (already a dependency) and the one-time `migrateFromSupabase()` described in section 2.6. Remove
   supabase-kt from dependencies; keep reading its stored session JSON through a tiny reader for the
   migration only.
4. **Backend selection behind a flag** `BACKEND = SUPABASE | CLOUDFLARE` in BuildKonfig, so debug builds can run
   either and the same code can be soaked on a device before the default flips.
5. **AI:** `AiProxyServiceImpl` only changes its URL and header (drop `apikey`). SSE format kept byte
   compatible so the parser does not change.
6. **Deep links:** keep `lifeplanner://auth` and the `tribe.az/lifeplanner/auth/callback` page; OTP code entry
   already exists, so OTP path needs no deep link at all. iOS `DeepLinkHandler` loses `handleDeeplinks` and
   parses the verify token itself.
7. **Google sign-in (new feature).** Android: Credential Manager / Google ID token sent to
   `POST /v1/auth/google` (Better Auth `idToken`). iOS: GoogleSignIn SDK. **App Store guideline 4.8:** adding a
   third-party login on iOS requires an equivalent option such as Sign in with Apple. Recommendation: ship
   Google on Android in 4.1, and on iOS together with Sign in with Apple in 4.2 (needs Apple Developer
   Services ID and key, which only Kamran can create).
8. **Tests:** existing syncer/viewmodel tests use the no-Supabase constructor and survive; `V4SyncSchemaTest`
   is repointed at the Worker's table registry; add contract tests that run the Worker locally (`wrangler dev`
   or Miniflare via vitest-pool-workers) against the same DTO fixtures.

## 5. Phases and estimate

Estimate for one person (me driving, Kamran doing the console steps in section 6): **about 4 weeks of focused
work, 5 to 6 calendar weeks** including store review and a soak period. The earlier "3 to 4 weeks" guess holds
only if the gamification port and anonymous-link behaviour go smoothly; I would plan 5 and be pleasantly
surprised. Phases ship nothing to users until the last one.

| Phase | What | Days | Exit check |
|---|---|---|---|
| 0 | This inventory and plan | done | doc committed |
| 1 | `cloudflare/` Worker skeleton, schema generator, D1 staging, data copy script, row-count verifier | 4 | staging D1 holds a faithful copy, counts match per table and per user |
| 2 | Sync API + table registry + `updated_at`/`sync_version` stamping; port gamification triggers and `update_daily_streak`; parity test replaying real event sequences against Supabase and D1 | 5 | XP, level, badges, challenge progress equal on 20 sampled real users |
| 3 | Better Auth on D1: anonymous, email+password, OTP (email provider), link-in-place, Google `idToken`, legacy bcrypt verify+rehash, `/auth/migrate` | 5 | all flows green in a local contract suite; legacy hash login works for a throwaway test account |
| 4 | ai-proxy streaming (OpenAI/Gemini/Grok), usage logs, share-journal, report-content, delete-account, persona webhook, auth-redirect, R2 assets, public content endpoints | 4 | SSE byte-compatible with current parser; delete-account cascades fully |
| 5 | Ops: Cron Triggers for health and store-watch, Telegram alerts, bridge job (Supabase to D1 delta), observability, rate limits, backups (D1 Time Travel + scheduled export to R2) | 2 | alerts fire on a forced failure |
| 6 | Client: sections 4.1 to 4.8, both platforms, flag-gated; dev builds on the real emulator/simulator with throwaway accounts only | 7 | in-place upgrade from a 4.0 build with a migrated guest AND a password user keeps all data on both platforms |
| 7 | Soak: internal track on Cloudflare for a week, watch D1 metrics, Sentry/Crashlytics, sync success rate; final delta copy; staged rollout 10% to 100% | 5 to 7 | no sync regressions vs 4.0 baseline |
| 8 | Sunset plan: stop bridge, announce, later switch Supabase off (months out, separate decision) | later | |

**Risks, ranked**

1. Gamification trigger parity (hidden business logic in plpgsql). Mitigated by the replay parity test.
2. Anonymous to email linking keeping the same user id (spike in Phase 3 day 1).
3. CPU limits on free Workers for hashing (section 2.5).
4. Timestamp format drift breaking LWW (`gt("updated_at")` is string compare). Mitigated by one stamping
   function and a test that mixes Supabase-origin and Worker-origin rows.
5. Split-brain while 4.0 and 4.1 coexist (bridge job narrows, does not remove).
6. `mcp-server` scope creep; explicitly deferred.
7. Free-plan Supabase pausing if its traffic drops (keep the health cron until shutdown).

## 6. What only Kamran can do

Roughly in the order needed. Everything else I can do with the Cloudflare MCP / wrangler.

1. **Cloudflare:** confirm which account/zone to use and whether the account is on Workers Paid (USD 5/month;
   recommended before Phase 3 for password hashing CPU). Authorise the DNS token / wrangler login for the
   `tribe.az` zone so I can create `lp-api.tribe.az`. (I can create D1, KV, R2 through the connected MCP.)
2. **Secrets, put by Kamran, never pasted in chat:** `OPENAI_API_KEY`, `GEMINI_API_KEY`, `GROK_API_KEY`,
   `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `PERSONA_WEBHOOK_SECRET` as Worker secrets
   (`wrangler secret put NAME` in the Terminal tab; I will give the exact commands). Supabase will not reveal
   them through the API, they need to come from wherever he stored them.
3. **Supabase dashboard (read only):** generate or confirm a service key access for the export script, or
   allow me to export via the Management API / MCP (the MCP already works for reads; for bulk export of
   `auth.users` hashes the SQL route is enough, so this may need nothing).
4. **Google Cloud (a dedicated LifePlanner project is recommended):** OAuth consent
   screen, a Web client (server verification), an Android client with the SHA-1 of the Play App Signing key
   and of the upload/debug keys, an iOS client. These are console and Play Console steps (App signing SHA-1).
5. **Email sender:** pick Resend (create account, verify `tribe.az`, API key as secret) or enable Cloudflare
   Email Service on the zone.
6. **Apple (for 4.2):** Sign in with Apple Services ID and key, if Google on iOS is wanted.
7. **Play Console / App Store:** 4.1 release uploads, data safety form update (data processor changes from
   Supabase to Cloudflare), privacy policy text update naming the new processor.
8. **Decisions I have taken but he can veto:** domain name `lp-api.tribe.az`; Google on Android first; Sign
   in with Apple deferred to 4.2; `mcp-server` deferred; Supabase kept alive for months.

## 7. Single next step

Phase 1: scaffold `cloudflare/` (Hono Worker, wrangler config, schema generator) and create the staging D1,
then run the first data copy into staging with row-count verification. No Kamran action needed to start;
the first thing I will ask for is the Workers Paid answer and the `lp-api` DNS go-ahead before Phase 3.
