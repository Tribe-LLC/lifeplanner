// Port of supabase/functions/ai-proxy, Anthropic Claude only (decided 2026-10-19: one provider, one key, no picker).
// Same request/response/SSE contract so AiProxyServiceImpl only changes its URL. Any `provider` field is ignored.
// Anthropic bills per token, so every request is capped (MAX_TOKENS, last MAX_MESSAGES turns) and each user has a daily quota.
// Dropped on purpose: the pgvector RAG branch (content_embeddings had 0 rows); the user-context block is kept.
import { Hono, type Context } from "hono";
import { resolveUser, type Env } from "./auth";

interface AiRequest {
  prompt?: string;
  systemPrompt?: string;
  messages?: { role: string; content: string }[];
  responseSchema?: Record<string, unknown>;
  provider?: string;
  enrichContext?: boolean;
  stream?: boolean;
}
interface TokenUsage { inputTokens: number; outputTokens: number }
interface AiResponse { text: string; provider: string; model: string; usage?: TokenUsage }

// Change the model here, or per environment with the ANTHROPIC_MODEL var, without touching the rest.
const DEFAULT_MODEL = "claude-haiku-4-5-20251001";
const MAX_TOKENS = 1024;
const MAX_TOKENS_STRUCTURED = 2048;
const MAX_MESSAGES = 30;
const DEFAULT_DAILY_LIMIT = 60; // requests per user per rolling 24 h, override with the AI_DAILY_LIMIT var
const PROVIDER = "ANTHROPIC";
const modelOf = (env: Env) => env.ANTHROPIC_MODEL || DEFAULT_MODEL;
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

export const aiRoutes = new Hono<{ Bindings: Env }>();

const handler = async (c: Context<{ Bindings: Env }>) => {
  const userId = await resolveUser(c);
  if (!userId) return json({ error: "Authentication required" }, 401);
  try {
    const body = (await c.req.json()) as AiRequest;
    if (!c.env.ANTHROPIC_API_KEY) return json({ error: "The AI is unavailable right now. Please try again in a moment." }, 503);
    if (await overDailyLimit(c.env, userId)) return json({ error: "You have reached today's AI limit. It resets within 24 hours." }, 429);
    if (body.messages && body.messages.length > MAX_MESSAGES) body.messages = body.messages.slice(-MAX_MESSAGES);

    if (body.enrichContext !== false && body.messages) {
      try {
        const ctx = await fetchUserContext(c.env.DB, userId);
        if (ctx) {
          body.systemPrompt = (body.systemPrompt ?? "") +
            "\n\nYou have access to the user's real-time data below. Use it to answer questions about their goals, habits, streaks, progress, etc. accurately.\n\n" + ctx;
        }
      } catch (e) { console.warn("Context enrichment error (non-fatal):", e); }
    }
    body.systemPrompt = (body.systemPrompt ?? "") +
      "\n\nWriting style: write like a real person in plain, natural language. Never use the long dash punctuation (the em dash or en dash) anywhere in your replies. Use commas, periods, colons, parentheses, or separate sentences instead.";

    const log = (r: AiResponse, type: string) => c.executionCtx.waitUntil(logUsage(c.env.DB, userId, r, type));

    if (body.stream === true && body.messages) return streamingResponse(c.env, body, (r) => log(r, "chat"));

    const result = await callClaude(c.env, body);
    log(result, body.messages ? "chat" : body.responseSchema ? "structured" : "generate");
    return json(result);
  } catch (error) {
    const raw = error instanceof Error ? error.message : "Unknown error occurred";
    console.error("AI proxy error:", raw);
    let msg = "Something went wrong with the AI request. Please try again.";
    if (raw.includes("rate limit") || raw.includes("429")) msg = "AI provider is rate-limited. Please wait a moment and try again.";
    else if (raw.includes("401") || raw.includes("403")) msg = "AI provider authentication failed. Please contact support.";
    else if (raw.includes("timeout") || raw.includes("ETIMEDOUT")) msg = "AI provider timed out. Please try again.";
    return json({ error: msg }, 500);
  }
};
aiRoutes.post("/v1/ai-proxy", handler);
aiRoutes.post("/v1/ai", handler);

// ── usage + user context (D1 instead of per-user PostgREST) ─────────────────────────────────

async function overDailyLimit(env: Env, userId: string): Promise<boolean> {
  const limit = Number(env.AI_DAILY_LIMIT) || DEFAULT_DAILY_LIMIT;
  try {
    const row = await env.DB.prepare("SELECT count(*) AS n FROM ai_usage_logs WHERE user_id = ? AND created_at > strftime('%Y-%m-%dT%H:%M:%fZ','now','-1 day')")
      .bind(userId).first<{ n: number }>();
    return (row?.n ?? 0) >= limit;
  } catch (e) { console.warn("Daily limit check failed (allowing):", e instanceof Error ? e.message : e); return false; }
}

async function logUsage(db: D1Database, userId: string, r: AiResponse, type: string) {
  try {
    await db.prepare("INSERT INTO ai_usage_logs (user_id, provider, model, input_tokens, output_tokens, request_type) VALUES (?,?,?,?,?,?)")
      .bind(userId, r.provider, r.model, r.usage?.inputTokens ?? 0, r.usage?.outputTokens ?? 0, type).run();
  } catch (e) { console.warn("Usage log failed:", e instanceof Error ? e.message : e); }
}

async function fetchUserContext(db: D1Database, uid: string): Promise<string | null> {
  const all = async (sql: string) => (await db.prepare(sql).bind(uid).all<Record<string, unknown>>()).results;
  const [profile, progress, goals, habits, journal, badges, focus] = await Promise.all([
    all("select display_name, profession, age_range, mindset from users where user_id = ? limit 1"),
    all("select * from user_progress where user_id = ? limit 1"),
    all("select title, category, status, progress, due_date from goals where user_id = ? and is_deleted = 0 and is_archived = 0 limit 20"),
    all("select title, category, current_streak, longest_streak, frequency from habits where user_id = ? and is_active = 1 and is_deleted = 0 limit 20"),
    all("select title, mood, date from journal_entries where user_id = ? and is_deleted = 0 order by date desc limit 10"),
    all("select badge_type, earned_at from badges where user_id = ? and is_deleted = 0 order by earned_at desc limit 20"),
    all("select goal_id, planned_duration_minutes, actual_duration_seconds, was_completed, started_at from focus_sessions where user_id = ? and is_deleted = 0 order by started_at desc limit 10"),
  ]);
  if (!profile[0] && !progress[0]) return null;
  return formatUserContext(profile[0] ?? null, progress[0] ?? null, goals, habits, journal, badges, focus);
}

type Row = Record<string, unknown>;
function formatUserContext(profile: Row | null, progress: Row | null, goals: Row[], habits: Row[], journal: Row[], badges: Row[], focus: Row[]): string {
  const lines: string[] = ["=== USER DATA ==="];
  if (profile) {
    const parts = [profile.display_name, profile.profession, profile.age_range].filter(Boolean);
    if (parts.length > 0) lines.push(`PROFILE: ${parts.join(", ")}`);
    if (profile.mindset) lines.push(`MINDSET: ${profile.mindset}`);
  }
  if (progress) {
    lines.push(`PROGRESS: Level ${progress.current_level ?? 1} (${progress.total_xp ?? 0} XP), Streak: ${progress.current_streak ?? 0} days (best: ${progress.longest_streak ?? 0}), ` +
      `Goals completed: ${progress.goals_completed ?? 0}, Habits done: ${progress.habits_completed ?? 0}, Journal entries: ${progress.journal_entries_count ?? 0}`);
  }
  const active = goals.filter((g) => g.status !== "COMPLETED" && g.status !== "ABANDONED");
  const done = goals.filter((g) => g.status === "COMPLETED");
  if (active.length) {
    lines.push("", `ACTIVE GOALS (${active.length}):`);
    active.forEach((g, i) => lines.push(`${i + 1}. "${g.title}" [${g.category}] ${g.progress}%${g.due_date ? ` due ${g.due_date}` : ""}`));
  }
  if (done.length) {
    lines.push("", `COMPLETED GOALS (${done.length}):`);
    done.forEach((g, i) => lines.push(`${i + 1}. "${g.title}" [${g.category}]`));
  }
  if (habits.length) {
    lines.push("", `HABITS (${habits.length} active):`);
    habits.forEach((h, i) => lines.push(`${i + 1}. "${h.title}" [${h.category}] ${h.frequency} streak:${h.current_streak} best:${h.longest_streak}`));
  }
  if (journal.length) {
    lines.push("", `RECENT JOURNAL (${journal.length}):`);
    journal.forEach((j, i) => lines.push(`${i + 1}. "${j.title}" ${j.mood} ${j.date}`));
  }
  if (badges.length) lines.push("", `BADGES (${badges.length}): ${badges.map((b) => b.badge_type).join(", ")}`);
  if (focus.length) {
    lines.push("", `RECENT FOCUS SESSIONS (${focus.length}):`);
    focus.forEach((f, i) => {
      const mins = Math.round((Number(f.actual_duration_seconds) || 0) / 60);
      lines.push(`${i + 1}. ${mins}/${f.planned_duration_minutes}min ${f.was_completed ? "completed" : "incomplete"} (${f.started_at})`);
    });
  }
  lines.push("=== END USER DATA ===");
  return lines.join("\n");
}

// ── Anthropic Messages API ─────────────────────────────────────────────────────────────────────

const ANTHROPIC = "https://api.anthropic.com/v1/messages";
const headers = (env: Env) => ({ "Content-Type": "application/json", "x-api-key": env.ANTHROPIC_API_KEY ?? "", "anthropic-version": "2023-06-01" });

function claudeRequest(env: Env, body: AiRequest, stream: boolean) {
  const raw = body.messages && body.messages.length > 0 ? body.messages : [{ role: "user", content: body.prompt ?? "" }];
  // Claude needs strictly alternating turns that start with the user: merge neighbours with the same role, drop a leading assistant turn.
  const messages: { role: "user" | "assistant"; content: string }[] = [];
  for (const m of raw) {
    const role = m.role === "assistant" || m.role === "model" ? "assistant" : "user";
    if (messages.length === 0 && role === "assistant") continue;
    const last = messages[messages.length - 1];
    if (last && last.role === role) last.content += "\n\n" + m.content; else messages.push({ role, content: m.content });
  }
  if (messages.length === 0) messages.push({ role: "user", content: body.prompt ?? "" });
  let system = body.systemPrompt ?? "";
  if (body.responseSchema) {
    system += "\n\nReply with a single JSON value that matches this JSON schema and nothing else (no prose, no code fences):\n" + JSON.stringify(body.responseSchema);
  }
  const req: Record<string, unknown> = { model: modelOf(env), max_tokens: body.responseSchema ? MAX_TOKENS_STRUCTURED : MAX_TOKENS, messages, stream };
  if (system.trim()) req.system = system.trim();
  return req;
}

const stripFence = (t: string) => t.replace(/^\s*```(?:json)?\s*/i, "").replace(/\s*```\s*$/, "");

async function callClaude(env: Env, body: AiRequest): Promise<AiResponse> {
  const r = await fetch(ANTHROPIC, { method: "POST", headers: headers(env), body: JSON.stringify(claudeRequest(env, body, false)) });
  if (!r.ok) throw new Error(`Anthropic API error ${r.status}: ${await r.text()}`);
  const d = (await r.json()) as any;
  const text = (d?.content ?? []).filter((b: any) => b.type === "text").map((b: any) => b.text).join("");
  return {
    text: body.responseSchema ? stripFence(text) : text, provider: PROVIDER, model: d?.model ?? modelOf(env),
    usage: d?.usage ? { inputTokens: d.usage.input_tokens ?? 0, outputTokens: d.usage.output_tokens ?? 0 } : undefined,
  };
}

// ── streaming (event: text|done|error, same bytes as before) ──────────────────────────────────

type Send = (event: string, data: string) => void;

function streamingResponse(env: Env, body: AiRequest, onDone: (r: AiResponse) => void): Response {
  const stream = new ReadableStream({
    async start(controller) {
      const enc = new TextEncoder();
      const send: Send = (event, data) => controller.enqueue(enc.encode(`event: ${event}\ndata: ${data}\n\n`));
      let streamedAny = false;
      const sendTracked: Send = (e, d) => { if (e === "text") streamedAny = true; send(e, d); };
      // One retry while nothing has reached the client (a transient 5xx should not surface mid-chat).
      for (let attempt = 0; attempt < 2; attempt++) {
        try {
          const usage = await streamClaude(env, body, sendTracked);
          send("done", JSON.stringify({ provider: PROVIDER, model: modelOf(env), usage: usage ?? { inputTokens: 0, outputTokens: 0 } }));
          onDone({ text: "", provider: PROVIDER, model: modelOf(env), usage });
          controller.close();
          return;
        } catch (err) {
          const raw = err instanceof Error ? err.message : "Stream error";
          if (!streamedAny && attempt === 0) { console.warn(`Claude stream failed before any output (${raw}), retrying once`); continue; }
          console.error("Stream error:", raw);
          send("error", JSON.stringify({ error: streamedAny
            ? (raw.includes("timeout") || raw.includes("ETIMEDOUT") ? "AI provider timed out. Please try again." : "Something went wrong during streaming. Please try again.")
            : "The AI is unavailable right now. Please try again in a moment." }));
          controller.close();
          return;
        }
      }
    },
  });
  return new Response(stream, { headers: { "Content-Type": "text/event-stream", "Cache-Control": "no-cache", "Connection": "keep-alive", "Access-Control-Allow-Origin": "*" } });
}

async function* sseData(res: Response): AsyncGenerator<string> {
  const reader = res.body!.getReader();
  const dec = new TextDecoder();
  let buf = "";
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buf += dec.decode(value, { stream: true });
    const lines = buf.split("\n");
    buf = lines.pop() ?? "";
    for (const line of lines) { const t = line.trim(); if (t.startsWith("data: ")) yield t.slice(6).trim(); }
  }
}

async function streamClaude(env: Env, body: AiRequest, send: Send): Promise<TokenUsage | undefined> {
  const r = await fetch(ANTHROPIC, { method: "POST", headers: headers(env), body: JSON.stringify(claudeRequest(env, body, true)) });
  if (!r.ok) throw new Error(`Anthropic streaming error ${r.status}: ${await r.text()}`);
  let inputTokens = 0, outputTokens = 0, seenUsage = false;
  for await (const data of sseData(r)) {
    if (!data) continue;
    let ev: any;
    try { ev = JSON.parse(data); } catch { continue; /* skip malformed chunk */ }
    if (ev.type === "content_block_delta" && ev.delta?.type === "text_delta" && ev.delta.text) send("text", ev.delta.text);
    else if (ev.type === "message_start" && ev.message?.usage) { inputTokens = ev.message.usage.input_tokens ?? 0; outputTokens = ev.message.usage.output_tokens ?? 0; seenUsage = true; }
    else if (ev.type === "message_delta" && ev.usage) { outputTokens = ev.usage.output_tokens ?? outputTokens; if (ev.usage.input_tokens) inputTokens = ev.usage.input_tokens; seenUsage = true; }
    else if (ev.type === "error") throw new Error(`Anthropic stream error: ${ev.error?.type ?? "unknown"}`);
  }
  return seenUsage ? { inputTokens, outputTokens } : undefined;
}
