-- Turn the raw Supabase copy (legacy_auth_users) into Better Auth users + credential accounts, SAME ids.
-- Idempotent. Guests have no email in Supabase: Better Auth needs one, so they get a placeholder on a reserved domain.
-- Run: npx wrangler d1 execute DB --remote --yes --file scripts/import-auth.sql
INSERT OR IGNORE INTO auth_user (id, name, email, emailVerified, createdAt, updatedAt, isAnonymous)
SELECT id,
       COALESCE(display_name, ''),
       CASE WHEN email IS NULL OR email = '' THEN 'guest-' || id || '@guest.invalid' ELSE lower(email) END,
       CASE WHEN email_confirmed_at IS NOT NULL THEN 1 ELSE 0 END,
       COALESCE(created_at, strftime('%Y-%m-%dT%H:%M:%fZ','now')),
       COALESCE(last_sign_in_at, created_at, strftime('%Y-%m-%dT%H:%M:%fZ','now')),
       CASE WHEN is_anonymous = 1 OR email IS NULL OR email = '' THEN 1 ELSE 0 END
FROM legacy_auth_users;

INSERT OR IGNORE INTO auth_account (id, accountId, providerId, userId, password, createdAt, updatedAt)
SELECT 'cred-' || id, id, 'credential', id, encrypted_password,
       COALESCE(created_at, strftime('%Y-%m-%dT%H:%M:%fZ','now')), COALESCE(created_at, strftime('%Y-%m-%dT%H:%M:%fZ','now'))
FROM legacy_auth_users
WHERE encrypted_password IS NOT NULL AND encrypted_password <> '';
