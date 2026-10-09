// Port of supabase/functions/ai-proxy, Gemini only (decided 2026-10-09: one provider, one key). Same request/response/SSE
// contract so AiProxyServiceImpl only changes its URL. A request naming OPENAI or GROK is answered by Gemini.
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

const MODEL = "gemini-2.5-flash";
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

export const aiRoutes = new Hono<{ Bindings: Env }>();

const handler = async (c: Context<{ Bindings: Env }>) => {
  const userId = await resolveUser(c);
  if (!userId) return json({ error: "Authentication required" }, 401);
  try {
    const body = (await c.req.json()) as AiRequest;
    if (!["GEMINI", "OPENAI", "GROK"].includes(body.provider ?? "GEMINI")) return json({ error: `Unsupported provider: ${body.provider}` }, 400);
    if (!c.env.GEMINI_API_KEY) return json({ error: "The AI is unavailable right now. Please try again in a moment." }, 503);

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

    const result = await callGemini(c.env, body);
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

// ── providers ──────────────────────────────────────────────────────────────────────────────────

const geminiHeaders = (env: Env) => ({ "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY ?? "" });
const GEMINI = "https://generativelanguage.googleapis.com/v1beta/models";

function geminiContents(body: AiRequest) {
  if (body.messages && body.messages.length > 0) {
    return body.messages.map((m) => ({ role: m.role === "assistant" ? "model" : m.role, parts: [{ text: m.content }] }));
  }
  return [{ role: "user", parts: [{ text: body.prompt ?? "" }] }];
}

async function callGemini(env: Env, body: AiRequest): Promise<AiResponse> {
  const model = MODEL;
  const req: Record<string, unknown> = { contents: geminiContents(body) };
  if (body.systemPrompt) req.systemInstruction = { parts: [{ text: body.systemPrompt }] };
  if (body.responseSchema) req.generationConfig = { responseMimeType: "application/json", responseSchema: body.responseSchema };
  const r = await fetch(`${GEMINI}/${model}:generateContent`, { method: "POST", headers: geminiHeaders(env), body: JSON.stringify(req) });
  if (!r.ok) throw new Error(`Gemini API error ${r.status}: ${await r.text()}`);
  const d = (await r.json()) as any;
  return {
    text: d?.candidates?.[0]?.content?.parts?.[0]?.text ?? "", provider: "GEMINI", model,
    usage: d?.usageMetadata ? { inputTokens: d.usageMetadata.promptTokenCount ?? 0, outputTokens: d.usageMetadata.candidatesTokenCount ?? 0 } : undefined,
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
          const usage = await streamGemini(env, body, MODEL, sendTracked);
          send("done", JSON.stringify({ provider: "GEMINI", model: MODEL, usage: usage ?? { inputTokens: 0, outputTokens: 0 } }));
          onDone({ text: "", provider: "GEMINI", model: MODEL, usage });
          controller.close();
          return;
        } catch (err) {
          const raw = err instanceof Error ? err.message : "Stream error";
          if (!streamedAny && attempt === 0) { console.warn(`Gemini stream failed before any output (${raw}), retrying once`); continue; }
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

async function streamGemini(env: Env, body: AiRequest, model: string, send: Send): Promise<TokenUsage | undefined> {
  const req: Record<string, unknown> = { contents: geminiContents(body) };
  if (body.systemPrompt) req.systemInstruction = { parts: [{ text: body.systemPrompt }] };
  const r = await fetch(`${GEMINI}/${model}:streamGenerateContent?alt=sse`, { method: "POST", headers: geminiHeaders(env), body: JSON.stringify(req) });
  if (!r.ok) throw new Error(`Gemini streaming error ${r.status}: ${await r.text()}`);
  let usage: TokenUsage | undefined;
  for await (const data of sseData(r)) {
    if (!data) continue;
    try {
      const chunk = JSON.parse(data);
      const text = chunk?.candidates?.[0]?.content?.parts?.[0]?.text;
      if (text) send("text", text);
      if (chunk?.usageMetadata) usage = { inputTokens: chunk.usageMetadata.promptTokenCount ?? 0, outputTokens: chunk.usageMetadata.candidatesTokenCount ?? 0 };
    } catch { /* skip malformed chunk */ }
  }
  return usage;
}
