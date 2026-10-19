import { env } from "cloudflare:workers";
import { SELF } from "cloudflare:test";
import { afterEach, describe, expect, it, vi } from "vitest";

const U = "55555555-5555-4555-8555-555555555555";
const auth = { Authorization: `Bearer dev:${U}`, "content-type": "application/json" };
const ai = (body: unknown) => SELF.fetch("http://localhost/v1/ai-proxy", { method: "POST", headers: auth, body: JSON.stringify(body) });

function mockProviders(handlers: Record<string, (body: any) => Response>, seen: any[] = []) {
  const real = globalThis.fetch;
  vi.spyOn(globalThis, "fetch").mockImplementation(async (input: any, init?: any) => {
    const url = typeof input === "string" ? input : input.url;
    for (const [host, h] of Object.entries(handlers)) {
      if (url.startsWith(host)) { const b = JSON.parse(init.body); seen.push({ url, body: b, headers: init.headers }); return h(b); }
    }
    return real(input, init);
  });
}
afterEach(() => vi.restoreAllMocks());


const anthropic = "https://api.anthropic.com";
const msg = (text: string, extra: object = {}) => Response.json({ model: "claude-haiku-4-5-20251001", content: [{ type: "text", text }], usage: { input_tokens: 3, output_tokens: 4 }, ...extra });
const sse = (events: object[]) => new Response(events.map((e: any) => `event: ${e.type}\ndata: ${JSON.stringify(e)}\n\n`).join(""), { headers: { "Content-Type": "text/event-stream" } });
const delta = (text: string) => ({ type: "content_block_delta", index: 0, delta: { type: "text_delta", text } });
const streamOf = (...texts: string[]) => [
  { type: "message_start", message: { usage: { input_tokens: 5, output_tokens: 1 } } },
  ...texts.map(delta),
  { type: "message_delta", delta: { stop_reason: "end_turn" }, usage: { output_tokens: 2 } },
  { type: "message_stop" },
];

describe("ai-proxy", () => {
  it("requires a signed-in user", async () => {
    expect((await SELF.fetch("http://localhost/v1/ai-proxy", { method: "POST", body: "{}" })).status).toBe(401);
  });

  it("calls Claude, appends the style rule, keeps the key in a header and logs usage", async () => {
    (env as any).ANTHROPIC_API_KEY = "a-key";
    const seen: any[] = [];
    mockProviders({ [anthropic]: () => msg("hello") }, seen);
    const r = await ai({ prompt: "hi", systemPrompt: "be kind" });
    expect(r.status).toBe(200);
    expect(await r.json()).toMatchObject({ text: "hello", provider: "ANTHROPIC", model: "claude-haiku-4-5-20251001", usage: { inputTokens: 3, outputTokens: 4 } });
    expect(seen[0].url).toBe("https://api.anthropic.com/v1/messages");
    expect(seen[0].url).not.toContain("a-key");
    expect(seen[0].headers["x-api-key"]).toBe("a-key");
    expect(seen[0].headers["anthropic-version"]).toBe("2023-06-01");
    expect(seen[0].body.model).toBe("claude-haiku-4-5-20251001");
    expect(seen[0].body.max_tokens).toBe(1024);
    expect(seen[0].body.system).toContain("be kind");
    expect(seen[0].body.system).toContain("Never use the long dash");
    expect(seen[0].body.messages).toEqual([{ role: "user", content: "hi" }]);
    await new Promise((r) => setTimeout(r, 50));
    const row = await env.DB.prepare("select provider, input_tokens, output_tokens, request_type from ai_usage_logs where user_id = ?").bind(U).first<any>();
    expect(row).toMatchObject({ provider: "ANTHROPIC", input_tokens: 3, output_tokens: 4, request_type: "generate" });
  });

  it("ignores any provider field (one provider, no picker)", async () => {
    (env as any).ANTHROPIC_API_KEY = "a-key";
    mockProviders({ [anthropic]: () => msg("claude answered") });
    for (const provider of ["OPENAI", "GEMINI", "NOPE"]) {
      expect(await (await ai({ provider, prompt: "hi" })).json()).toMatchObject({ text: "claude answered", provider: "ANTHROPIC" });
    }
  });

  it("maps chat turns to Claude roles and merges neighbours", async () => {
    (env as any).ANTHROPIC_API_KEY = "a-key";
    const seen: any[] = [];
    mockProviders({ [anthropic]: () => msg("ok") }, seen);
    await ai({ enrichContext: false, messages: [{ role: "model", content: "welcome" }, { role: "user", content: "a" }, { role: "user", content: "b" }, { role: "model", content: "c" }, { role: "user", content: "d" }] });
    expect(seen[0].body.messages).toEqual([{ role: "user", content: "a\n\nb" }, { role: "assistant", content: "c" }, { role: "user", content: "d" }]);
  });

  it("asks for JSON when a response schema is given", async () => {
    (env as any).ANTHROPIC_API_KEY = "a-key";
    const seen: any[] = [];
    mockProviders({ [anthropic]: () => msg("```json\n{\"a\":1}\n```") }, seen);
    const body = (await (await ai({ prompt: "x", responseSchema: { type: "object" } })).json()) as any;
    expect(body.text).toBe('{"a":1}');
    expect(seen[0].body.system).toContain("JSON schema");
    expect(seen[0].body.max_tokens).toBe(2048);
  });

  it("is unavailable, not crashing, when no key is configured", async () => {
    (env as any).ANTHROPIC_API_KEY = "";
    expect((await ai({ prompt: "hi" })).status).toBe(503);
  });

  it("sanitises upstream errors", async () => {
    (env as any).ANTHROPIC_API_KEY = "a-key";
    mockProviders({ [anthropic]: () => new Response("secret upstream detail", { status: 500 }) });
    const r = await ai({ prompt: "hi" });
    expect(r.status).toBe(500);
    expect(await r.text()).not.toContain("secret upstream");
  });

  it("enriches chat with the user's own data", async () => {
    (env as any).ANTHROPIC_API_KEY = "a-key";
    await env.DB.prepare("insert into goals (id,user_id,category,title,description,status,timeline,due_date,progress) values ('ctx1',?,'CAREER','Ship v4','','ACTIVE','x','2026-12-01',40)").bind(U).run();
    const seen: any[] = [];
    mockProviders({ [anthropic]: () => msg("ok") }, seen);
    await ai({ messages: [{ role: "user", content: "how am I doing" }] });
    expect(seen[0].body.system).toContain('"Ship v4" [CAREER] 40% due 2026-12-01');
  });

  it("stops a user at the daily limit with a 429 and never calls Claude", async () => {
    (env as any).ANTHROPIC_API_KEY = "a-key";
    const V = "66666666-6666-4666-8666-666666666666";
    (env as any).AI_DAILY_LIMIT = "2";
    for (let i = 0; i < 2; i++) await env.DB.prepare("insert into ai_usage_logs (user_id, provider, model) values (?,?,?)").bind(V, "ANTHROPIC", "m").run();
    await env.DB.prepare("insert into ai_usage_logs (user_id, provider, model, created_at) values (?,?,?, '2020-01-01T00:00:00.000Z')").bind(U + "-old", "ANTHROPIC", "m").run();
    const seen: any[] = [];
    mockProviders({ [anthropic]: () => msg("no") }, seen);
    const r = await SELF.fetch("http://localhost/v1/ai-proxy", { method: "POST", headers: { ...auth, Authorization: `Bearer dev:${V}` }, body: JSON.stringify({ prompt: "hi" }) });
    expect(r.status).toBe(429);
    expect(seen.length).toBe(0);
    (env as any).AI_DAILY_LIMIT = "";
  });

  it("streams text then done as SSE, same event shapes as before", async () => {
    (env as any).ANTHROPIC_API_KEY = "a-key";
    const seen: any[] = [];
    mockProviders({ [anthropic]: () => sse(streamOf("Hel", "lo")) }, seen);
    const r = await ai({ stream: true, enrichContext: false, messages: [{ role: "user", content: "hi" }] });
    expect(r.headers.get("content-type")).toContain("text/event-stream");
    expect(seen[0].body.stream).toBe(true);
    const text = await r.text();
    expect(text).toBe('event: text\ndata: Hel\n\nevent: text\ndata: lo\n\nevent: done\ndata: {"provider":"ANTHROPIC","model":"claude-haiku-4-5-20251001","usage":{"inputTokens":5,"outputTokens":2}}\n\n');
  });

  it("streaming retries once before any output, then reports failure as an error event", async () => {
    (env as any).ANTHROPIC_API_KEY = "a-key";
    let n = 0;
    mockProviders({ [anthropic]: () => (n++ === 0 ? new Response("boom", { status: 529 }) : sse(streamOf("hey"))) });
    const ok = await (await ai({ stream: true, enrichContext: false, messages: [{ role: "user", content: "hi" }] })).text();
    expect(ok).toContain("event: text\ndata: hey");
    expect(ok).toContain('"provider":"ANTHROPIC"');
    vi.restoreAllMocks();
    mockProviders({ [anthropic]: () => new Response("boom", { status: 529 }) });
    const bad = await (await ai({ stream: true, enrichContext: false, messages: [{ role: "user", content: "hi" }] })).text();
    expect(bad).toContain("event: error");
  });
});
