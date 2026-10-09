-- Better Auth tables (names prefixed so they never read like the app's `users` profile table).
-- Shapes follow better-auth 1.7 getAuthTables() for: core + anonymous + emailOTP + bearer. Dates are ISO TEXT.
CREATE TABLE IF NOT EXISTS "auth_user" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "name" TEXT NOT NULL DEFAULT '',
  "email" TEXT NOT NULL UNIQUE,
  "emailVerified" INTEGER NOT NULL DEFAULT 0,
  "image" TEXT,
  "createdAt" TEXT NOT NULL,
  "updatedAt" TEXT NOT NULL,
  "isAnonymous" INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS "auth_session" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "expiresAt" TEXT NOT NULL,
  "token" TEXT NOT NULL UNIQUE,
  "createdAt" TEXT NOT NULL,
  "updatedAt" TEXT NOT NULL,
  "ipAddress" TEXT,
  "userAgent" TEXT,
  "userId" TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS "ix_auth_session_user" ON "auth_session" ("userId");
CREATE TABLE IF NOT EXISTS "auth_account" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "accountId" TEXT NOT NULL,
  "providerId" TEXT NOT NULL,
  "userId" TEXT NOT NULL,
  "accessToken" TEXT,
  "refreshToken" TEXT,
  "idToken" TEXT,
  "accessTokenExpiresAt" TEXT,
  "refreshTokenExpiresAt" TEXT,
  "scope" TEXT,
  "password" TEXT,
  "createdAt" TEXT NOT NULL,
  "updatedAt" TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS "ix_auth_account_user" ON "auth_account" ("userId");
CREATE UNIQUE INDEX IF NOT EXISTS "uq_auth_account_provider" ON "auth_account" ("providerId", "accountId");
CREATE TABLE IF NOT EXISTS "auth_verification" (
  "id" TEXT NOT NULL PRIMARY KEY,
  "identifier" TEXT NOT NULL,
  "value" TEXT NOT NULL,
  "expiresAt" TEXT NOT NULL,
  "createdAt" TEXT NOT NULL,
  "updatedAt" TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS "ix_auth_verification_identifier" ON "auth_verification" ("identifier");
