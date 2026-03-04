# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest  | Yes       |

## Reporting a Vulnerability

If you discover a security vulnerability in LifePlanner, please report it responsibly:

1. **Do not** open a public GitHub issue for security vulnerabilities
2. Email **kamran@tribe.az** with a description of the vulnerability
3. Include steps to reproduce if possible
4. Allow reasonable time for a fix before public disclosure

## Security Practices

- **No client-side API keys:** All AI requests route through a server-side Supabase Edge Function proxy
- **Authentication:** Firebase Auth + Supabase Auth with row-level security
- **Data privacy:** User data stays on-device by default; cloud sync is opt-in
- **Soft-delete:** Data is never permanently removed without explicit user action
- **JWT verification:** All edge functions require valid authentication tokens

## Scope

This policy covers the LifePlanner application code and its Supabase Edge Functions. Third-party dependencies are managed through Gradle and monitored for known vulnerabilities.
