#!/bin/bash
# Load scripts/out/*.sql into the D1 bound as DB. gamification_log goes FIRST: the gamification triggers
# (migrations/0003) skip any event already logged, so copied goals/habits/etc. never re-award XP.
set -euo pipefail
cd "$(dirname "$0")/.."
export CLOUDFLARE_ACCOUNT_ID=${CLOUDFLARE_ACCOUNT_ID:-16708206509436d326b3d9041f8873cd}
TARGET=${1:---remote}
for f in scripts/out/gamification_log.sql $(ls scripts/out/*.sql | grep -v '/gamification_log.sql'); do
  [ -s "$f" ] || continue
  npx wrangler d1 execute DB "$TARGET" --yes --file "$f" 2>&1 | grep -iE "✘|error" || true
done
echo imported
