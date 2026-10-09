import type { Env } from "./auth";

/**
 * Transactional email. Phase 3: Resend over HTTPS when RESEND_API_KEY is set (free tier), otherwise logged
 * (dev/tests). Swap for Cloudflare Email Service here if that is enabled on the zone.
 */
export async function sendEmail(env: Env, m: { to: string; subject: string; text: string }) {
  if (!env.RESEND_API_KEY) {
    console.log(`[email:dev] to=${m.to} subject=${m.subject} body=${m.text}`);
    (globalThis as any).__lastEmail = m;
    return;
  }
  const r = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${env.RESEND_API_KEY}`, "content-type": "application/json" },
    body: JSON.stringify({ from: env.EMAIL_FROM ?? "LifePlanner <no-reply@tribe.az>", to: m.to, subject: m.subject, text: m.text }),
  });
  if (!r.ok) throw new Error(`email send failed: ${r.status}`);
}
