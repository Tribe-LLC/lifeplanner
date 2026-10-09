# LifePlanner Cloudflare backend (4.1)

Plan: [../docs/MIGRATION-cloudflare.md](../docs/MIGRATION-cloudflare.md). Phase 1 state: skeleton Worker, D1 schema
generated from Supabase, read-only data copy and verifier. No client talks to this yet.

| Thing | Where |
|---|---|
| Account | Com3run@gmail.com's Account (`16708206509436d326b3d9041f8873cd`) |
| Staging D1 | `lifeplanner-staging` (`c7fc9fef-e776-43c3-a392-40a4bd06499f`) |
| Worker | `lifeplanner-api` (not deployed yet) |

```bash
export CLOUDFLARE_ACCOUNT_ID=16708206509436d326b3d9041f8873cd
python3 scripts/gen-d1-schema.py                 # pg-schema.json -> migrations/0001_init.sql
npx wrangler d1 migrations apply DB --remote     # staging
python3 scripts/copy-from-supabase.py [--since ISO] [--tables a,b]   # SELECT-only, writes scripts/out/*.sql
./scripts/import-d1.sh
python3 scripts/verify-copy.py                   # counts + max(updated_at) per table
```

`scripts/pg-schema.json` is a dump of the live `public` schema (refresh it when Supabase schema changes).
The copy reads Supabase through the Management API using the Supabase CLI login in the macOS keychain.
`scripts/out/` is gitignored (it contains user data). `legacy_auth_users` holds bcrypt hashes: staging only.
