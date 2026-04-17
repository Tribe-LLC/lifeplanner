import type { Metadata } from 'next';
import { getAdminSupabase } from '@/lib/admin-supabase';

export const metadata: Metadata = { title: 'Overview — Admin' };

const POSTHOG_EMBEDS = [
  { label: 'Daily Active Users', envKey: 'NEXT_PUBLIC_POSTHOG_DAU_URL' },
  { label: 'Retention', envKey: 'NEXT_PUBLIC_POSTHOG_RETENTION_URL' },
  { label: 'Onboarding Funnel', envKey: 'NEXT_PUBLIC_POSTHOG_FUNNEL_URL' },
];

async function getStats() {
  if (!process.env.SUPABASE_SERVICE_ROLE_KEY) return null;
  try {
    const db = getAdminSupabase();
    const today = new Date().toISOString().split('T')[0];

    const [
      usersRes, totalGoalsRes, completedGoalsRes,
      totalHabitsRes, chatSessionsRes, aiMessagesRes,
      journalRes, checkInsTodayRes, aiUsageRes,
    ] = await Promise.all([
      db.from('users').select('*', { count: 'exact', head: true }),
      db.from('goals').select('*', { count: 'exact', head: true }),
      db.from('goals').select('*', { count: 'exact', head: true }).eq('status', 'COMPLETED'),
      db.from('habits').select('*', { count: 'exact', head: true }),
      db.from('chat_sessions').select('*', { count: 'exact', head: true }),
      db.from('chat_messages').select('*', { count: 'exact', head: true }).eq('role', 'assistant'),
      db.from('journal_entries').select('*', { count: 'exact', head: true }),
      db.from('habit_check_ins').select('*', { count: 'exact', head: true }).eq('date', today),
      db.from('ai_usage_logs').select('input_tokens,output_tokens'),
    ]);

    const totalUsers = usersRes.count ?? 0;
    const totalGoals = totalGoalsRes.count ?? 0;
    const completedGoals = completedGoalsRes.count ?? 0;
    const totalHabits = totalHabitsRes.count ?? 0;
    const totalTokens = (aiUsageRes.data ?? []).reduce(
      (sum, r) => sum + (r.input_tokens ?? 0) + (r.output_tokens ?? 0), 0,
    );

    return {
      totalUsers,
      totalGoals,
      completedGoals,
      goalCompletionRate: totalGoals ? Math.round((completedGoals / totalGoals) * 100) : 0,
      avgGoalsPerUser: totalUsers ? (totalGoals / totalUsers).toFixed(1) : '—',
      totalHabits,
      avgHabitsPerUser: totalUsers ? (totalHabits / totalUsers).toFixed(1) : '—',
      totalChatSessions: chatSessionsRes.count ?? 0,
      aiMessages: aiMessagesRes.count ?? 0,
      journalEntries: journalRes.count ?? 0,
      checkInsToday: checkInsTodayRes.count ?? 0,
      totalTokens,
    };
  } catch {
    return null;
  }
}

function StatCard({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl px-5 py-4">
      <p className="text-xs text-gray-500 mb-1">{label}</p>
      <p className="text-2xl font-bold text-white">{value}</p>
      {sub && <p className="text-xs text-gray-500 mt-0.5">{sub}</p>}
    </div>
  );
}

export default async function AdminOverviewPage() {
  const stats = await getStats();

  const embeds = POSTHOG_EMBEDS.map((e) => ({
    label: e.label,
    url: process.env[e.envKey] ?? null,
  }));
  const hasEmbeds = embeds.some((e) => e.url);

  return (
    <div className="p-8 space-y-10">
      <div>
        <h1 className="text-2xl font-bold mb-1">Overview</h1>
        <p className="text-sm text-gray-400">Live stats from Supabase + PostHog insights.</p>
      </div>

      {/* Stats grid */}
      {stats ? (
        <div className="space-y-4">
          {/* Row 1 — Users */}
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-widest">Users</p>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard label="Total users" value={stats.totalUsers} />
            <StatCard label="Total chat sessions" value={stats.totalChatSessions} />
            <StatCard label="Journal entries" value={stats.journalEntries} />
            <StatCard label="Habit check-ins today" value={stats.checkInsToday} />
          </div>

          {/* Row 2 — Goals */}
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-widest pt-2">Goals</p>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard label="Total goals" value={stats.totalGoals} />
            <StatCard label="Completed goals" value={stats.completedGoals} sub={`${stats.goalCompletionRate}% completion rate`} />
            <StatCard label="Avg goals / user" value={stats.avgGoalsPerUser} />
            <StatCard label="Total habits" value={stats.totalHabits} sub={`avg ${stats.avgHabitsPerUser} per user`} />
          </div>

          {/* Row 3 — AI */}
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-widest pt-2">AI Usage</p>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard label="AI responses sent" value={stats.aiMessages.toLocaleString()} />
            <StatCard label="Total tokens used" value={stats.totalTokens > 1_000_000
              ? `${(stats.totalTokens / 1_000_000).toFixed(1)}M`
              : stats.totalTokens > 1_000
              ? `${(stats.totalTokens / 1_000).toFixed(0)}k`
              : stats.totalTokens} />
            <StatCard label="Avg AI replies / session"
              value={stats.totalChatSessions
                ? (stats.aiMessages / stats.totalChatSessions).toFixed(1)
                : '—'} />
            <StatCard label="AI msgs / user"
              value={stats.totalUsers
                ? (stats.aiMessages / stats.totalUsers).toFixed(1)
                : '—'} />
          </div>
        </div>
      ) : (
        <div className="rounded-xl border border-dashed border-gray-700 bg-gray-900 p-6 text-sm text-gray-500">
          Stats unavailable — set <code className="text-indigo-400">SUPABASE_SERVICE_ROLE_KEY</code> in Vercel env vars.
        </div>
      )}

      {/* PostHog embeds */}
      <div>
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-widest mb-4">PostHog Insights</p>
        {!hasEmbeds ? (
          <div className="rounded-xl border border-dashed border-gray-700 bg-gray-900 p-8 text-center max-w-xl">
            <p className="text-sm font-semibold text-gray-300 mb-2">PostHog embeds not configured</p>
            <p className="text-xs text-gray-500 leading-relaxed mb-3">
              PostHog → Insight → Share → Enable public sharing → copy URL → add env var:
            </p>
            <ul className="space-y-1">
              {POSTHOG_EMBEDS.map((e) => (
                <li key={e.envKey} className="text-xs font-mono text-indigo-400">{e.envKey}=…</li>
              ))}
            </ul>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {embeds.map(({ label, url }) =>
              url ? (
                <div key={label} className="rounded-xl overflow-hidden border border-gray-800 bg-gray-900">
                  <div className="px-4 py-3 border-b border-gray-800">
                    <p className="text-sm font-semibold">{label}</p>
                  </div>
                  <iframe src={url} className="w-full" style={{ height: 320, border: 'none' }} loading="lazy" />
                </div>
              ) : (
                <div key={label} className="rounded-xl border border-dashed border-gray-700 bg-gray-900 flex items-center justify-center" style={{ height: 368 }}>
                  <p className="text-xs text-gray-600">{label} — not configured</p>
                </div>
              )
            )}
          </div>
        )}
      </div>
    </div>
  );
}
