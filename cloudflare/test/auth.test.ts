import { env } from "cloudflare:workers";
import { SELF } from "cloudflare:test";
import { describe, expect, it, vi } from "vitest";
import bcrypt from "bcryptjs";

const j = { "content-type": "application/json" };
const call = (path: string, init: RequestInit = {}) => SELF.fetch(`http://localhost${path}`, init);
const post = (path: string, body: unknown, token?: string) =>
  call(path, { method: "POST", headers: { ...j, ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(body) });
const tokenOf = (r: Response) => r.headers.get("set-auth-token")!;
const lastMail = () => (globalThis as any).__lastEmail as { to: string; text: string };
const code = () => /(\d{6})/.exec(lastMail().text)![1];

describe("anonymous (guest)", () => {
  it("signs in as a guest and the token authorises sync calls", async () => {
    const r = await post("/v1/auth/sign-in/anonymous", {});
    expect(r.status).toBe(200);
    const token = tokenOf(r);
    expect(token).toBeTruthy();
    const body = (await r.json()) as any;
    expect(body.user.isAnonymous).toBe(true);
    const pull = await call("/v1/sync/goals", { headers: { Authorization: `Bearer ${token}` } });
    expect(pull.status).toBe(200);
    expect(await call("/v1/sync/goals").then((x) => x.status)).toBe(401);
  });
});

describe("email + password", () => {
  it("signs up, signs in, rejects a wrong password", async () => {
    const up = await post("/v1/auth/sign-up/email", { email: "Ann@Example.com", password: "secret12", name: "Ann" });
    expect(up.status).toBe(200);
    const ok = await post("/v1/auth/sign-in/email", { email: "ann@example.com", password: "secret12" });
    expect(ok.status).toBe(200);
    expect(tokenOf(ok)).toBeTruthy();
    expect((await post("/v1/auth/sign-in/email", { email: "ann@example.com", password: "nope-nope" })).status).toBe(401);
  });
});

describe("legacy Supabase bcrypt accounts", () => {
  it("logs in with the old hash and rehashes it on first success", async () => {
    const hash = await bcrypt.hash("old-password", 4); // $2b$, same family as Supabase's $2a$
    const now = new Date().toISOString();
    await env.DB.batch([
      env.DB.prepare("INSERT INTO auth_user (id,name,email,emailVerified,createdAt,updatedAt,isAnonymous) VALUES (?,?,?,?,?,?,0)")
        .bind("legacy-1", "Old", "old@example.com", 1, now, now),
      env.DB.prepare("INSERT INTO auth_account (id,accountId,providerId,userId,password,createdAt,updatedAt) VALUES (?,?,?,?,?,?,?)")
        .bind("cred-legacy-1", "legacy-1", "credential", "legacy-1", hash, now, now),
    ]);
    expect((await post("/v1/auth/sign-in/email", { email: "old@example.com", password: "wrong-one" })).status).toBe(401);
    const r = await post("/v1/auth/sign-in/email", { email: "old@example.com", password: "old-password" });
    expect(r.status).toBe(200);
    expect(((await r.json()) as any).user.id).toBe("legacy-1"); // same user id as in Supabase
    const row = await env.DB.prepare("SELECT password FROM auth_account WHERE id = 'cred-legacy-1'").first<{ password: string }>();
    expect(row!.password.startsWith("$2")).toBe(false);
    expect((await post("/v1/auth/sign-in/email", { email: "old@example.com", password: "old-password" })).status).toBe(200);
  });
});

describe("email code sign-in", () => {
  it("sends a code and signs the user in with it", async () => {
    await post("/v1/auth/sign-up/email", { email: "otp@example.com", password: "secret12", name: "Otp" });
    expect((await post("/v1/auth/email-otp/send-verification-otp", { email: "otp@example.com", type: "sign-in" })).status).toBe(200);
    expect(lastMail().to).toBe("otp@example.com");
    const r = await post("/v1/auth/sign-in/email-otp", { email: "otp@example.com", otp: code() });
    expect(r.status).toBe(200);
    expect(tokenOf(r)).toBeTruthy();
  });
});

describe("guest -> email account in place", () => {
  it("keeps the user id and the data", async () => {
    const g = await post("/v1/auth/sign-in/anonymous", {});
    const token = tokenOf(g);
    const id = ((await g.json()) as any).user.id as string;
    await env.DB.prepare("INSERT INTO habits (id,user_id,title,description,category,frequency,target_count,created_at) VALUES ('hh',?,'t','','c','DAILY',1,'x')").bind(id).run();
    expect((await post("/v1/auth/link-email/start", { email: "link@example.com", password: "secret12", displayName: "Lin" }, token)).status).toBe(200);
    expect((await post("/v1/auth/link-email/confirm", { otp: "000000" }, token)).status).toBe(400);
    const done = await post("/v1/auth/link-email/confirm", { otp: code() }, token);
    expect(done.status).toBe(200);
    expect(((await done.json()) as any).user).toMatchObject({ id, email: "link@example.com", isAnonymous: false });
    const login = await post("/v1/auth/sign-in/email", { email: "link@example.com", password: "secret12" });
    expect(((await login.json()) as any).user.id).toBe(id);
    expect(await env.DB.prepare("SELECT count(*) c FROM habits WHERE user_id = ?").bind(id).first("c")).toBe(1);
  });
});

describe("Supabase session migration", () => {
  it("exchanges a valid Supabase token for a session on the same user id", async () => {
    const real = globalThis.fetch;
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input: any, init?: any) => {
      const url = typeof input === "string" ? input : input.url;
      if (url.startsWith("https://supabase.test/auth/v1/user")) {
        return Response.json(init?.headers?.Authorization === "Bearer good"
          ? { id: "5a5a5a5a-0000-4000-8000-000000000001", email: null, is_anonymous: true }
          : { msg: "bad" }, { status: init?.headers?.Authorization === "Bearer good" ? 200 : 401 });
      }
      return real(input, init);
    });
    (env as any).SUPABASE_URL = "https://supabase.test"; (env as any).SUPABASE_ANON_KEY = "anon";
    expect((await post("/v1/auth/migrate", {}, "bad")).status).toBe(401);
    const r = await post("/v1/auth/migrate", {}, "good");
    expect(r.status).toBe(200);
    const body = (await r.json()) as any;
    expect(body.user).toMatchObject({ id: "5a5a5a5a-0000-4000-8000-000000000001", isAnonymous: true, email: null });
    const pull = await call("/v1/sync/habits", { headers: { Authorization: `Bearer ${body.token}` } });
    expect(pull.status).toBe(200);
    vi.restoreAllMocks();
  });
});

describe("delete account", () => {
  it("removes the user's rows and login", async () => {
    const g = await post("/v1/auth/sign-in/anonymous", {});
    const token = tokenOf(g);
    const id = ((await g.json()) as any).user.id as string;
    await env.DB.prepare("INSERT INTO habits (id,user_id,title,description,category,frequency,target_count,created_at) VALUES ('del1',?,'t','','c','DAILY',1,'x')").bind(id).run();
    expect((await post("/v1/auth/delete-account", {}, token)).status).toBe(200);
    expect(await env.DB.prepare("SELECT count(*) c FROM habits WHERE user_id = ?").bind(id).first("c")).toBe(0);
    expect(await env.DB.prepare("SELECT count(*) c FROM auth_user WHERE id = ?").bind(id).first("c")).toBe(0);
    expect((await call("/v1/sync/habits", { headers: { Authorization: `Bearer ${token}` } })).status).toBe(401);
  });
});
