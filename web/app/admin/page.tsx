import type { Metadata } from 'next';

export const metadata: Metadata = { title: 'Analytics — Admin' };

// PostHog shareable embed URLs — set these in Vercel env vars once you create
// shared insights in your PostHog dashboard (Share → Enable sharing → copy URL).
const POSTHOG_EMBEDS = [
  { label: 'Daily Active Users', envKey: 'NEXT_PUBLIC_POSTHOG_DAU_URL' },
  { label: 'Retention', envKey: 'NEXT_PUBLIC_POSTHOG_RETENTION_URL' },
  { label: 'Onboarding Funnel', envKey: 'NEXT_PUBLIC_POSTHOG_FUNNEL_URL' },
];

export default function AdminAnalyticsPage() {
  const embeds = POSTHOG_EMBEDS.map((e) => ({
    label: e.label,
    url: process.env[e.envKey] ?? null,
  }));

  const hasEmbeds = embeds.some((e) => e.url);

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold mb-1">Analytics</h1>
      <p className="text-sm text-gray-400 mb-8">PostHog insights embedded below.</p>

      {!hasEmbeds && (
        <div className="rounded-xl border border-dashed border-gray-700 bg-gray-900 p-8 text-center max-w-xl">
          <p className="text-sm font-semibold text-gray-300 mb-2">PostHog embeds not configured</p>
          <p className="text-xs text-gray-500 leading-relaxed">
            In PostHog, open an insight → Share → Enable public sharing → copy the URL.
            Add it as an env var:
          </p>
          <ul className="mt-3 space-y-1">
            {POSTHOG_EMBEDS.map((e) => (
              <li key={e.envKey} className="text-xs font-mono text-indigo-400">
                {e.envKey}=https://us.posthog.com/shared/…
              </li>
            ))}
          </ul>
        </div>
      )}

      {hasEmbeds && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {embeds.map(({ label, url }) =>
            url ? (
              <div key={label} className="rounded-xl overflow-hidden border border-gray-800 bg-gray-900">
                <div className="px-4 py-3 border-b border-gray-800">
                  <p className="text-sm font-semibold">{label}</p>
                </div>
                <iframe
                  src={url}
                  className="w-full"
                  style={{ height: 320, border: 'none' }}
                  loading="lazy"
                />
              </div>
            ) : (
              <div
                key={label}
                className="rounded-xl border border-dashed border-gray-700 bg-gray-900 flex items-center justify-center"
                style={{ height: 368 }}
              >
                <p className="text-xs text-gray-600">{label} — not configured</p>
              </div>
            )
          )}
        </div>
      )}
    </div>
  );
}
