-- Raw copy of Supabase auth.users, input for the Better Auth import in Phase 3.
-- Holds bcrypt hashes: keep this table out of any export and drop it once the import is verified.
CREATE TABLE IF NOT EXISTS "legacy_auth_users" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "email" TEXT,
  "is_anonymous" INTEGER NOT NULL DEFAULT 0,
  "email_confirmed_at" TEXT,
  "created_at" TEXT,
  "last_sign_in_at" TEXT,
  "display_name" TEXT,
  "provider" TEXT,
  "encrypted_password" TEXT
);
