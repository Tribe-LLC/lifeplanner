import { Hono } from "hono";
import { cors } from "hono/cors";
import { resolveUser, type Env } from "./auth";
import { BadRequest, fromDb, nowIso, normTs, upsertStatement } from "./db";
import { awardBadgesStatement } from "./badges";
import { authRoutes } from "./authroutes";
import { aiRoutes } from "./ai";
import { SYNC_TABLES, TABLES } from "./tables";

const app = new Hono<{ Bindings: Env; Variables: { userId: string } }>();
app.use("*", cors({ origin: "*", allowHeaders: ["authorization", "content-type"], allowMethods: ["GET", "POST", "OPTIONS"] }));

app.onError((err, c) => {
  if (err instanceof BadRequest) return c.json({ error: err.message }, 400);
  const msg = err instanceof Error ? err.message : String(err);
  if (/UNIQUE constraint failed/i.test(msg)) return c.json({ error: "conflict", detail: msg }, 409);
  console.error("unhandled", msg);
  return c.json({ error: "internal" }, 500);
});

app.get("/health", async (c) => {
  const row = await c.env.DB.prepare("select count(*) as n from sqlite_master where type='table'").first<{ n: number }>();
  return c.json({ ok: true, tables: row?.n ?? 0, time: nowIso() });
});

const authed = async (c: any, next: () => Promise<void>) => {
  const uid = await resolveUser(c);
  if (!uid) return c.json({ error: "Not signed in" }, 401);
  c.set("userId", uid);
  await next();
};

app.route("/", authRoutes);
app.route("/", aiRoutes);

// ---- sync: replaces postgrest[t].upsert(dtos) and postgrest[t].select { eq(user_id); gt(updated_at) } ----

app.get("/v1/sync/:table", authed, async (c) => {
  const table = c.req.param("table");
  if (!SYNC_TABLES.includes(table)) return c.json({ error: "unknown table" }, 404);
  const def = TABLES[table];
  const since = c.req.query("since");
  const args: unknown[] = [c.get("userId")];
  let sql = `select * from "${table}" where "user_id" = ?`;
  if (since && def.synced) { sql += ` and "updated_at" > ?`; args.push(normTs(since)); }
  const { results } = await c.env.DB.prepare(sql).bind(...args).all<Record<string, unknown>>();
  return c.json(results.map((r) => fromDb(table, r)));
});

app.post("/v1/sync/:table", authed, async (c) => {
  const table = c.req.param("table");
  if (!SYNC_TABLES.includes(table)) return c.json({ error: "unknown table" }, 404);
  const def = TABLES[table];
  const userId = c.get("userId");
  const body = await c.req.json().catch(() => null);
  const rows: Record<string, unknown>[] = Array.isArray(body) ? body : body ? [body] : [];
  for (const r of rows) {
    if (typeof r !== "object" || r === null) throw new BadRequest("rows must be objects");
    if (def.owned) {
      if (r.user_id !== undefined && r.user_id !== userId) return c.json({ error: "row belongs to another user" }, 403);
      r.user_id = userId;
    }
  }
  if (rows.length === 0) return c.json({ written: 0 });

  // A key that already belongs to someone else must fail the whole request (as RLS did), before any write.
  if (def.owned && !def.pk.includes("user_id") && def.pk.length === 1) {
    const pk = def.pk[0];
    for (let i = 0; i < rows.length; i += 90) {
      const ids = rows.slice(i, i + 90).map((r) => r[pk]);
      const { results } = await c.env.DB.prepare(
        `select 1 from "${table}" where "${pk}" in (${ids.map(() => "?").join(",")}) and "user_id" <> ? limit 1`,
      ).bind(...ids, userId).all();
      if (results.length) return c.json({ error: "row belongs to another user" }, 403);
    }
  }

  for (let i = 0; i < rows.length; i += 100) {
    await c.env.DB.batch(rows.slice(i, i + 100).map((r) => upsertStatement(c.env.DB, table, r, userId)));
  }
  return c.json({ written: rows.length });
});

// ---- public content (replaces anon-key PostgREST reads; RLS public_read filters are fixed here) ----

type Pub = { where: string; order: string; filters: string[] };
const PUBLIC: Record<string, Pub> = {
  builtin_coaches: { where: "", order: "display_order", filters: ["is_active"] },
  system_prompts: { where: "", order: "key", filters: [] },
  stories: { where: `"is_active" = 1`, order: "sort_order", filters: [] },
  coach_posts: { where: `"is_published" = 1`, order: "created_at", filters: ["coach_id"] },
  blog_posts: { where: `"published" = 1`, order: "created_at", filters: ["slug"] },
  personas_cache: { where: "", order: "persona_id", filters: [] },
};

app.get("/v1/content/:table", async (c) => {
  const table = c.req.param("table");
  const p = PUBLIC[table];
  if (!p) return c.json({ error: "unknown table" }, 404);
  const def = TABLES[table];
  const where = p.where ? [p.where] : [];
  const args: unknown[] = [];
  for (const f of p.filters) {
    const v = c.req.query(f);
    if (v === undefined) continue;
    where.push(`"${f}" = ?`);
    args.push(def.cols[f] === "b" ? (v === "true" || v === "1" ? 1 : 0) : v);
  }
  const ord = c.req.query("order") ?? `${p.order}.asc`;
  const [oc, od] = ord.split(".");
  if (!(oc in def.cols)) throw new BadRequest("bad order column");
  const sql = `select * from "${table}"${where.length ? " where " + where.join(" and ") : ""} order by "${oc}" ${od === "desc" ? "desc" : "asc"}`;
  const { results } = await c.env.DB.prepare(sql).bind(...args).all<Record<string, unknown>>();
  return c.json(results.map((r) => fromDb(table, r)));
});

// ---- public assets (coach pictures, welcome videos) from R2; served by the Worker so no extra domain is needed ----
app.get("/v1/assets/*", async (c) => {
  const key = decodeURIComponent(c.req.path.replace(/^\/v1\/assets\//, ""));
  if (!key || key.includes("..")) return c.json({ error: "not found" }, 404);
  const range = c.req.header("Range");
  const obj = await c.env.ASSETS.get(key, range ? { range: c.req.raw.headers } : undefined);
  if (!obj) return c.json({ error: "not found" }, 404);
  const h = new Headers();
  obj.writeHttpMetadata(h);
  h.set("etag", obj.httpEtag);
  h.set("cache-control", "public, max-age=31536000, immutable");
  h.set("accept-ranges", "bytes");
  h.set("access-control-allow-origin", "*");
  if (c.req.header("If-None-Match") === obj.httpEtag) return new Response(null, { status: 304, headers: h });
  if (range && "range" in obj && obj.range) {
    const r = obj.range as { offset?: number; length?: number };
    const start = r.offset ?? 0, len = r.length ?? obj.size - start;
    h.set("content-range", `bytes ${start}-${start + len - 1}/${obj.size}`);
    h.set("content-length", String(len));
    return new Response(obj.body, { status: 206, headers: h });
  }
  h.set("content-length", String(obj.size));
  return new Response(obj.body, { headers: h });
});

// ---- the user's own AI usage (ai_usage_logs RLS: select own) ----
app.get("/v1/ai-usage", authed, async (c) => {
  const since = c.req.query("since");
  const { results } = await c.env.DB.prepare(
    `select * from ai_usage_logs where user_id = ?${since ? " and created_at >= ?" : ""}`,
  ).bind(...(since ? [c.get("userId"), normTs(since)] : [c.get("userId")])).all<Record<string, unknown>>();
  return c.json(results.map((r) => fromDb("ai_usage_logs", r)));
});

// ---- rpc update_daily_streak (port of the plpgsql function; UTC day like CURRENT_DATE) ----
app.post("/v1/rpc/update_daily_streak", authed, async (c) => {
  const uid = c.get("userId");
  const db = c.env.DB;
  const today = new Date().toISOString().slice(0, 10);
  const row = await db.prepare("select last_check_in_date, current_streak, longest_streak from user_progress where user_id = ?")
    .bind(uid).first<{ last_check_in_date: string | null; current_streak: number; longest_streak: number }>();
  const award = (xp: number) => db.prepare(
    `insert into user_progress (user_id, total_xp, current_level, updated_at)
       values (?1, ?2, (select max(level) from level_thresholds where min_xp <= ?2), ?3)
       on conflict (user_id) do update set total_xp = user_progress.total_xp + excluded.total_xp,
         current_level = (select max(level) from level_thresholds where min_xp <= user_progress.total_xp + excluded.total_xp),
         updated_at = ?3, sync_version = user_progress.sync_version + 1`).bind(uid, xp, nowIso());
  const badges = awardBadgesStatement(db, uid);
  const reply = (s: number, x: number) => c.json([{ new_streak: s, xp_earned: x, xp_awarded: x }]);

  if (!row) {
    await db.batch([
      db.prepare(`insert or ignore into user_progress (user_id, current_streak, last_check_in_date, longest_streak, updated_at) values (?, 1, ?, 1, ?)`)
        .bind(uid, today, nowIso()),
      award(5), ...badges,
    ]);
    return reply(1, 5);
  }
  if (row.last_check_in_date === today) return reply(row.current_streak, 0);
  const days = row.last_check_in_date
    ? Math.round((Date.parse(today) - Date.parse(row.last_check_in_date)) / 86400000) : 0;
  const streak = row.last_check_in_date && days === 1 ? row.current_streak + 1 : 1;
  await db.batch([
    db.prepare(`update user_progress set current_streak = ?, last_check_in_date = ?, longest_streak = ?, updated_at = ?, sync_version = sync_version + 1 where user_id = ?`)
      .bind(streak, today, Math.max(streak, row.longest_streak ?? 0), nowIso(), uid),
    award(5), ...badges,
  ]);
  return reply(streak, 5);
});

export default app;
