import { env } from "cloudflare:workers";
import { SELF } from "cloudflare:test";
import { describe, expect, it } from "vitest";

const U = "11111111-1111-4111-8111-111111111111";
const V = "22222222-2222-4222-8222-222222222222";
const h = (u: string) => ({ Authorization: `Bearer dev:${u}`, "content-type": "application/json" });
const post = (t: string, u: string, body: unknown) => SELF.fetch(`https://x/v1/sync/${t}`, { method: "POST", headers: h(u), body: JSON.stringify(body) });
const get = (path: string, u: string) => SELF.fetch(`https://x${path}`, { headers: h(u) });

const habit = (id: string, over: Record<string, unknown> = {}) => ({
  id, user_id: U, title: "Read", description: "", category: "GROWTH", frequency: "DAILY", target_count: 1,
  current_streak: 0, longest_streak: 0, total_completions: 0, last_completed_date: null, linked_goal_id: null,
  correlation_score: 0, is_active: true, created_at: "2026-10-01T10:00:00Z", reminder_time: null, type: "BOOLEAN",
  unit: "", health_metric_type: null, health_target: null, completion_source: "MANUAL",
  updated_at: "2026-10-01T10:00:00Z", is_deleted: false, sync_version: 0, ...over,
});

describe("auth", () => {
  it("rejects anonymous callers", async () => {
    expect((await SELF.fetch("https://x/v1/sync/habits")).status).toBe(401);
  });
});

describe("sync", () => {
  it("round-trips booleans and keeps client values on insert", async () => {
    expect((await post("habits", U, [habit("h1")])).status).toBe(200);
    const rows = (await (await get("/v1/sync/habits", U)).json()) as any[];
    expect(rows).toHaveLength(1);
    expect(rows[0].is_active).toBe(true);
    expect(rows[0].is_deleted).toBe(false);
    expect(rows[0].updated_at).toBe("2026-10-01T10:00:00.000Z");
    expect(rows[0].sync_version).toBe(0);
  });

  it("on conflict overwrites, stamps server updated_at and bumps sync_version (update_sync_metadata)", async () => {
    await post("habits", U, [habit("h2")]);
    await post("habits", U, [habit("h2", { title: "Read more", updated_at: "2020-01-01T00:00:00Z" })]);
    const row = ((await (await get("/v1/sync/habits", U)).json()) as any[]).find((r) => r.id === "h2");
    expect(row.title).toBe("Read more");
    expect(row.sync_version).toBe(1);
    expect(Date.parse(row.updated_at)).toBeGreaterThan(Date.parse("2026-10-09T00:00:00Z"));
  });

  it("pull honours since in any ISO shape", async () => {
    await post("habits", U, [habit("h3")]);
    const all = ((await (await get("/v1/sync/habits", U)).json()) as any[]).length;
    const none = ((await (await get("/v1/sync/habits?since=2999-01-01T00:00:00Z", U)).json()) as any[]).length;
    const some = ((await (await get("/v1/sync/habits?since=2026-10-01T09:59:59.9999999Z", U)).json()) as any[]).length;
    expect(none).toBe(0);
    expect(some).toBeGreaterThan(0);
    expect(all).toBeGreaterThanOrEqual(some);
  });

  it("never exposes or overwrites another user's rows", async () => {
    await post("habits", U, [habit("mine")]);
    expect(((await (await get("/v1/sync/habits", V)).json()) as any[]).some((r) => r.id === "mine")).toBe(false);
    const steal = await post("habits", V, [habit("mine", { user_id: V, title: "stolen" })]);
    expect(steal.status).toBe(403);
    const row = ((await (await get("/v1/sync/habits", U)).json()) as any[]).find((r) => r.id === "mine");
    expect(row.title).toBe("Read");
    expect((await post("habits", V, [habit("x", { user_id: U })])).status).toBe(403);
  });

  it("rejects unknown columns and unknown tables", async () => {
    expect((await post("habits", U, [habit("bad", { nope: 1 })])).status).toBe(400);
    expect((await post("auth_users", U, [])).status).toBe(404);
  });

  it("stores json columns as json and returns them parsed", async () => {
    const entry = { id: "j1", user_id: U, title: "t", content: "c", mood: "NEUTRAL", tags: ["a", "b"], date: "2026-10-09",
      created_at: "2026-10-09T08:00:00Z", updated_at: "2026-10-09T08:00:00Z", is_deleted: false, sync_version: 0 };
    expect((await post("journal_entries", U, [entry])).status).toBe(200);
    const row = ((await (await get("/v1/sync/journal_entries", U)).json()) as any[])[0];
    expect(row.tags).toEqual(["a", "b"]);
  });

  it("composite-key tables upsert on (user_id, id)", async () => {
    const trip = { id: "t1", user_id: U, destination: "Bali", start_date: "2026-11-01", end_date: "2026-11-10", travel_mode: 0,
      created_at: "2026-10-01T00:00:00Z", updated_at: "2026-10-01T00:00:00Z", is_deleted: false, sync_version: 0 };
    const first = await post("trips", U, [trip]);
    expect(await first.clone().text()).toContain("written");
    expect((await post("trips", U, [{ ...trip, destination: "Ubud" }])).status).toBe(200);
    const rows = (await (await get("/v1/sync/trips", U)).json()) as any[];
    expect(rows).toHaveLength(1);
    expect(rows[0].destination).toBe("Ubud");
    expect(rows[0].sync_version).toBe(1);
  });
});

describe("gamification triggers", () => {
  const U = "33333333-3333-4333-8333-333333333333";
  const V = "44444444-4444-4444-8444-444444444444";
  const goal = (id: string, over: Record<string, unknown> = {}) => ({
    id, user_id: U, category: "CAREER", title: "g", description: "", status: "ACTIVE", timeline: "SHORT_TERM", due_date: "2026-12-01",
    progress: 0, notes: "", created_at: "2026-10-01T00:00:00Z", completion_rate: 0, is_archived: false,
    updated_at: "2026-10-01T00:00:00Z", is_deleted: false, sync_version: 0, ...over,
  });
  it("awards xp once per goal and completes on the update path", async () => {
    await post("goals", U, [goal("g1")]);
    await post("goals", U, [goal("g1")]); // re-push: update path, no second award
    let p = await env.DB.prepare("select total_xp from user_progress where user_id=?").bind(U).first<{ total_xp: number }>();
    expect(p?.total_xp).toBe(5);
    await post("goals", U, [goal("g1", { status: "COMPLETED" })]);
    p = await env.DB.prepare("select total_xp, goals_completed, current_level from user_progress where user_id=?").bind(U).first<any>();
    expect(p.total_xp).toBe(55);
    expect(p.goals_completed).toBe(1);
    const b = await env.DB.prepare("select badge_type from badges where user_id=? order by badge_type").bind(U).all<{ badge_type: string }>();
    expect(b.results.map((x) => x.badge_type)).toEqual(expect.arrayContaining(["FIRST_STEP", "GOAL_1"]));
  });

  it("daily streak rpc starts, repeats and is idempotent within a day", async () => {
    const call = async () => ((await (await SELF.fetch("https://x/v1/rpc/update_daily_streak", { method: "POST", headers: h(V) })).json()) as any)[0];
    const a = await call();
    expect(a.new_streak).toBe(1);
    expect(a.xp_earned).toBe(5);
    const b = await call();
    expect(b.new_streak).toBe(1);
    expect(b.xp_earned).toBe(0);
  });
});
