import { Hono } from "hono";
import { hashPassword } from "better-auth/crypto";
import { createAuth } from "./betterauth";
import { resolveUser, type Env } from "./auth";
import { nowIso } from "./db";
import { TABLES } from "./tables";
import { sendEmail } from "./email";

export const authRoutes = new Hono<{ Bindings: Env }>();

const uuid = () => crypto.randomUUID();
const sha256 = async (s: string) =>
  [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)))].map((b) => b.toString(16).padStart(2, "0")).join("");

/**
 * Exchange a still-valid Supabase access token for a Cloudflare session of the SAME user id.
 * The token is verified with a read-only call to Supabase; no Supabase secret is needed or stored.
 */
authRoutes.post("/v1/auth/migrate", async (c) => {
  const h = c.req.header("Authorization") ?? "";
  if (!/^Bearer\s+\S+/i.test(h) || !c.env.SUPABASE_URL || !c.env.SUPABASE_ANON_KEY) return c.json({ error: "Not signed in" }, 401);
  const r = await fetch(`${c.env.SUPABASE_URL}/auth/v1/user`, { headers: { Authorization: h, apikey: c.env.SUPABASE_ANON_KEY } });
  if (!r.ok) return c.json({ error: "Supabase session is not valid" }, 401);
  const su = (await r.json()) as { id?: string; email?: string | null; is_anonymous?: boolean; email_confirmed_at?: string | null; user_metadata?: { display_name?: string } };
  if (!su.id) return c.json({ error: "Supabase session is not valid" }, 401);

  const now = nowIso();
  const email = su.email ? su.email.toLowerCase() : `guest-${su.id}@guest.invalid`;
  // Signed up after the last import: create the user from what Supabase says (no password; they can reset it).
  await c.env.DB.prepare(
    `INSERT OR IGNORE INTO auth_user (id, name, email, emailVerified, createdAt, updatedAt, isAnonymous) VALUES (?,?,?,?,?,?,?)`,
  ).bind(su.id, su.user_metadata?.display_name ?? "", email, su.email_confirmed_at ? 1 : 0, now, now, su.email ? 0 : 1).run();

  const ctx = await createAuth(c.env, new URL(c.req.url).origin).$context;
  const session = await ctx.internalAdapter.createSession(su.id);
  const user = await c.env.DB.prepare("SELECT id, email, name, isAnonymous FROM auth_user WHERE id = ?").bind(su.id).first<any>();
  return c.json({ token: session.token, user: { id: user.id, email: user.isAnonymous ? null : user.email, name: user.name, isAnonymous: !!user.isAnonymous } });
});

/** Guest -> email account, in place (same user id). Step 1: store the pending credentials and mail a code. */
authRoutes.post("/v1/auth/link-email/start", async (c) => {
  const uid = await resolveUser(c);
  if (!uid) return c.json({ error: "Not signed in" }, 401);
  const b = await c.req.json<{ email?: string; password?: string; displayName?: string }>().catch(() => ({} as any));
  const email = (b.email ?? "").trim().toLowerCase();
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return c.json({ error: "Please enter a valid email address." }, 400);
  if (!b.password || b.password.length < 6) return c.json({ error: "Password is too short. Please use at least 6 characters." }, 400);
  const taken = await c.env.DB.prepare("SELECT id FROM auth_user WHERE email = ? AND id <> ?").bind(email, uid).first();
  if (taken) return c.json({ error: "This email is already associated with another account. Try signing in instead." }, 409);

  const otp = String(Math.floor(Math.random() * 1_000_000)).padStart(6, "0");
  const value = JSON.stringify({ otp: await sha256(otp), hash: await hashPassword(b.password), name: b.displayName ?? "", tries: 0 });
  const id = `link-email:${uid}`;
  const now = nowIso();
  await c.env.DB.batch([
    c.env.DB.prepare("DELETE FROM auth_verification WHERE identifier = ?").bind(id),
    c.env.DB.prepare("INSERT INTO auth_verification (id, identifier, value, expiresAt, createdAt, updatedAt) VALUES (?,?,?,?,?,?)")
      .bind(uuid(), id, JSON.stringify({ email, ...JSON.parse(value) }), new Date(Date.now() + 10 * 60_000).toISOString(), now, now),
  ]);
  await sendEmail(c.env, { to: email, subject: "Verify your email", text: `Your code is ${otp}. It expires in 10 minutes.` });
  return c.json({ sent: true });
});

/** Step 2: the code turns the guest into an email account. */
authRoutes.post("/v1/auth/link-email/confirm", async (c) => {
  const uid = await resolveUser(c);
  if (!uid) return c.json({ error: "Not signed in" }, 401);
  const { otp } = await c.req.json<{ otp?: string }>().catch(() => ({} as any));
  const id = `link-email:${uid}`;
  const row = await c.env.DB.prepare("SELECT id, value, expiresAt FROM auth_verification WHERE identifier = ?").bind(id).first<{ id: string; value: string; expiresAt: string }>();
  if (!row || Date.parse(row.expiresAt) < Date.now()) return c.json({ error: "Code expired. Please request a new verification email." }, 400);
  const v = JSON.parse(row.value) as { email: string; otp: string; hash: string; name: string; tries: number };
  if (v.tries >= 5) return c.json({ error: "Too many attempts. Please request a new code." }, 429);
  if (!otp || (await sha256(otp)) !== v.otp) {
    await c.env.DB.prepare("UPDATE auth_verification SET value = ? WHERE id = ?").bind(JSON.stringify({ ...v, tries: v.tries + 1 }), row.id).run();
    return c.json({ error: "Invalid code. Please check and try again." }, 400);
  }
  const taken = await c.env.DB.prepare("SELECT id FROM auth_user WHERE email = ? AND id <> ?").bind(v.email, uid).first();
  if (taken) return c.json({ error: "This email is already associated with another account. Try signing in instead." }, 409);
  const now = nowIso();
  await c.env.DB.batch([
    c.env.DB.prepare("UPDATE auth_user SET email = ?, emailVerified = 1, isAnonymous = 0, name = CASE WHEN ? <> '' THEN ? ELSE name END, updatedAt = ? WHERE id = ?")
      .bind(v.email, v.name, v.name, now, uid),
    c.env.DB.prepare("DELETE FROM auth_account WHERE userId = ? AND providerId = 'credential'").bind(uid),
    c.env.DB.prepare("INSERT INTO auth_account (id, accountId, providerId, userId, password, createdAt, updatedAt) VALUES (?,?,?,?,?,?,?)")
      .bind(uuid(), uid, "credential", uid, v.hash, now, now),
    c.env.DB.prepare("DELETE FROM auth_verification WHERE id = ?").bind(row.id),
  ]);
  return c.json({ user: { id: uid, email: v.email, name: v.name, isAnonymous: false } });
});

/** Replaces the delete-account edge function: removes every row of the caller, then the login itself. */
authRoutes.post("/v1/auth/delete-account", async (c) => {
  const uid = await resolveUser(c);
  if (!uid) return c.json({ error: "Not signed in" }, 401);
  const stmts = Object.entries(TABLES).filter(([, d]) => d.owned).map(([n]) => c.env.DB.prepare(`DELETE FROM "${n}" WHERE "user_id" = ?`).bind(uid));
  stmts.push(
    c.env.DB.prepare("DELETE FROM auth_session WHERE userId = ?").bind(uid),
    c.env.DB.prepare("DELETE FROM auth_account WHERE userId = ?").bind(uid),
    c.env.DB.prepare("DELETE FROM auth_verification WHERE identifier LIKE ?").bind(`%${uid}%`),
    c.env.DB.prepare("DELETE FROM auth_user WHERE id = ?").bind(uid),
  );
  await c.env.DB.batch(stmts);
  return c.json({ success: true });
});

// Everything else under /v1/auth is Better Auth itself (sign-in, sign-up, anonymous, email-otp, social, sessions).
authRoutes.on(["GET", "POST"], "/v1/auth/*", (c) => createAuth(c.env, new URL(c.req.url).origin).handler(c.req.raw));
