import type { Context } from "hono";
import { createAuth } from "./betterauth";

export type Env = {
  DB: D1Database;
  ASSETS: R2Bucket;
  /** Test only. When "1", a bearer token of the form `dev:<user-id>` is accepted as that user. Never set in deploys. */
  DEV_AUTH?: string;
  BASE_URL?: string;
  ANTHROPIC_API_KEY?: string;
  ANTHROPIC_MODEL?: string;
  AI_DAILY_LIMIT?: string;
  AUTH_SECRET: string;
  SUPABASE_URL?: string;
  SUPABASE_ANON_KEY?: string;
  RESEND_API_KEY?: string;
  EMAIL_FROM?: string;
  GOOGLE_WEB_CLIENT_ID?: string;
  GOOGLE_ANDROID_CLIENT_ID?: string;
  GOOGLE_IOS_CLIENT_ID?: string;
  GOOGLE_WEB_CLIENT_SECRET?: string;
  APPLE_SERVICE_ID?: string;
  APPLE_BUNDLE_ID?: string;
  APPLE_CLIENT_SECRET?: string;
};

/**
 * Resolve the signed-in user id from the request: a Better Auth session sent as `Authorization: Bearer <token>`.
 * Returns null when the request is not authenticated.
 */
export async function resolveUser(c: Context<{ Bindings: Env }>): Promise<string | null> {
  const h = c.req.header("Authorization") ?? "";
  const m = /^Bearer\s+(.+)$/i.exec(h);
  if (!m) return null;
  const token = m[1];
  if (c.env.DEV_AUTH === "1" && token.startsWith("dev:")) return token.slice(4) || null;
  const session = await createAuth(c.env, new URL(c.req.url).origin).api.getSession({ headers: c.req.raw.headers });
  return session?.user.id ?? null;
}
