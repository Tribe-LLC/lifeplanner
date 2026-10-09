import type { Context } from "hono";

export type Env = {
  DB: D1Database;
  /** Test only. When "1", a bearer token of the form `dev:<user-id>` is accepted as that user. Never set in deploys. */
  DEV_AUTH?: string;
};

/**
 * Resolve the signed-in user id from the request. Phase 2 stub: Better Auth sessions land in Phase 3.
 * Returns null when the request is not authenticated.
 */
export async function resolveUser(c: Context<{ Bindings: Env }>): Promise<string | null> {
  const h = c.req.header("Authorization") ?? "";
  const m = /^Bearer\s+(.+)$/i.exec(h);
  if (!m) return null;
  const token = m[1];
  if (c.env.DEV_AUTH === "1" && token.startsWith("dev:")) return token.slice(4) || null;
  return null;
}
