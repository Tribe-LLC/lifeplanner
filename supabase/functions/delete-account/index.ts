// Deletes the calling user's account and everything tied to it (Google Play account-deletion policy).
// POST with the user's own session token. Needs a valid JWT, so it can only ever delete the caller.
//
// Almost every table references auth.users with ON DELETE CASCADE, so removing the login removes the
// rows. Two tables (beginner_objectives, scheduled_posts) are NO ACTION and would block it, so they
// are cleared first.
import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, content-type, apikey, x-client-info",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: cors });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL")!;
  const anon = Deno.env.get("SUPABASE_ANON_KEY")!;
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ")) return json({ error: "Not signed in" }, 401);

  // Who is asking, from their own token.
  const asUser = createClient(url, anon, { global: { headers: { Authorization: auth } } });
  const { data, error } = await asUser.auth.getUser();
  if (error || !data.user) return json({ error: "Not signed in" }, 401);
  const userId = data.user.id;

  const admin = createClient(url, service);
  for (const table of ["beginner_objectives", "scheduled_posts"]) {
    const { error: e } = await admin.from(table).delete().eq("user_id", userId);
    if (e) return json({ error: `Could not clear ${table}`, detail: e.message }, 500);
  }
  const { error: delErr } = await admin.auth.admin.deleteUser(userId);
  if (delErr) return json({ error: "Could not delete the account", detail: delErr.message }, 500);
  return json({ success: true });
});
