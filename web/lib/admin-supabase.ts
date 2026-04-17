import { createClient } from '@supabase/supabase-js';

// Server-side client using service role key — bypasses RLS.
// Only import this in Server Components or Server Actions, never ship to the browser.
export function getAdminSupabase() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL!;
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY!;
  return createClient(url, key, { auth: { persistSession: false } });
}
