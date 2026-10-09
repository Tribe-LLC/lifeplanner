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

const gemini = "https://generativelanguage.googleapis.com";
const sse = (lines: string[]) => new Response(lines.map((l) => `data: ${l}\n\n`).join(""), { headers: { "Content-Type": "text/event-stream" } });

describe("ai-proxy", () => {
  it("requires a signed-in user", async () => {
    expect((await SELF.fetch("http://localhost/v1/ai-proxy", { method: "POST", body: "{}" })).status).toBe(401);
  });

  it("rejects an unknown provider", async () => {
    expect((await ai({ provider: "NOPE", prompt: "x" })).status).toBe(400);
  });

  it("calls Gemini, appends the style rule, keeps the key out of the URL and logs usage", async () => {
    (env as any).GEMINI_API_KEY = "g-key";
    const seen: any[] = [];
    mockProviders({ [gemini]: () => Response.json({ candidates: [{ content: { parts: [{ text: "hello" }] } }], usageMetadata: { promptTokenCount: 3, candidatesTokenCount: 4 } }) }, seen);
    const r = await ai({ provider: "GEMINI", prompt: "hi", systemPrompt: "be kind" });
    expect(r.status).toBe(200);
    expect(await r.json()).toMatchObject({ text: "hello", provider: "GEMINI", model: "gemini-2.5-flash", usage: { inputTokens: 3, outputTokens: 4 } });
    expect(seen[0].url).not.toContain("g-key");
    expect(seen[0].body.systemInstruction.parts[0].text).toContain("be kind");
    expect(seen[0].body.systemInstruction.parts[0].text).toContain("Never use the long dash");
    await new Promise((r) => setTimeout(r, 50));
    const row = await env.DB.prepare("select provider, input_tokens, output_tokens, request_type from ai_usage_logs where user_id = ?").bind(U).first<any>();
    expect(row).toMatchObject({ provider: "GEMINI", input_tokens: 3, output_tokens: 4, request_type: "generate" });
  });

  it("answers OPENAI or GROK requests with Gemini (one provider)", async () => {
    (env as any).GEMINI_API_KEY = "g-key";
    mockProviders({ [gemini]: () => Response.json({ candidates: [{ content: { parts: [{ text: "gemini answered" }] } }] }) });
    const body = (await (await ai({ provider: "OPENAI", prompt: "hi" })).json()) as any;
    expect(body).toMatchObject({ text: "gemini answered", provider: "GEMINI" });
  });

  it("is unavailable, not crashing, when no key is configured", async () => {
    (env as any).GEMINI_API_KEY = "";
    expect((await ai({ provider: "GEMINI", prompt: "hi" })).status).toBe(503);
  });

  it("sanitises upstream errors", async () => {
    (env as any).GEMINI_API_KEY = "g-key";
    mockProviders({ [gemini]: () => new Response("secret upstream detail", { status: 500 }) });
    const r = await ai({ provider: "GEMINI", prompt: "hi" });
    expect(r.status).toBe(500);
    expect(await r.text()).not.toContain("secret upstream");
  });

  it("enriches chat with the user's own data", async () => {
    (env as any).GEMINI_API_KEY = "g-key";
    await env.DB.prepare("insert into goals (id,user_id,category,title,description,status,timeline,due_date,progress) values ('ctx1',?,'CAREER','Ship v4','','ACTIVE','x','2026-12-01',40)").bind(U).run();
    const seen: any[] = [];
    mockProviders({ [gemini]: () => Response.json({ candidates: [{ content: { parts: [{ text: "ok" }] } }] }) }, seen);
    await ai({ provider: "GEMINI", messages: [{ role: "user", content: "how am I doing" }] });
    expect(seen[0].body.systemInstruction.parts[0].text).toContain('"Ship v4" [CAREER] 40% due 2026-12-01');
  });

  it("streams text then done as SSE, same bytes as before", async () => {
    (env as any).GEMINI_API_KEY = "g-key";
    mockProviders({ [gemini]: () => sse([
      JSON.stringify({ candidates: [{ content: { parts: [{ text: "Hel" }] } }] }),
      JSON.stringify({ candidates: [{ content: { parts: [{ text: "lo" }] } }], usageMetadata: { promptTokenCount: 5, candidatesTokenCount: 2 } }),
    ]) });
    const r = await ai({ provider: "GEMINI", stream: true, enrichContext: false, messages: [{ role: "user", content: "hi" }] });
    expect(r.headers.get("content-type")).toContain("text/event-stream");
    const text = await r.text();
    expect(text).toBe('event: text\ndata: Hel\n\nevent: text\ndata: lo\n\nevent: done\ndata: {"provider":"GEMINI","model":"gemini-2.5-flash","usage":{"inputTokens":5,"outputTokens":2}}\n\n');
  });

  it("streaming retries once before any output, then reports failure as an error event", async () => {
    (env as any).GEMINI_API_KEY = "g-key";
    let n = 0;
    mockProviders({ [gemini]: () => (n++ === 0 ? new Response("boom", { status: 503 }) : sse([JSON.stringify({ candidates: [{ content: { parts: [{ text: "hey" }] } }] })])) });
    const ok = await (await ai({ provider: "GEMINI", stream: true, enrichContext: false, messages: [{ role: "user", content: "hi" }] })).text();
    expect(ok).toContain("event: text\ndata: hey");
    expect(ok).toContain('"provider":"GEMINI"');
    vi.restoreAllMocks();
    mockProviders({ [gemini]: () => new Response("boom", { status: 503 }) });
    const bad = await (await ai({ provider: "GEMINI", stream: true, enrichContext: false, messages: [{ role: "user", content: "hi" }] })).text();
    expect(bad).toContain("event: error");
  });
});
