import { Hono } from "hono";

type Env = { DB: D1Database };
const app = new Hono<{ Bindings: Env }>();

// Phase 1 skeleton: health only. Sync API, auth and ai-proxy land in later phases
// (see docs/MIGRATION-cloudflare.md).
app.get("/health", async (c) => {
  const row = await c.env.DB.prepare("select count(*) as n from sqlite_master where type='table'").first<{ n: number }>();
  return c.json({ ok: true, tables: row?.n ?? 0 });
});

export default app;
