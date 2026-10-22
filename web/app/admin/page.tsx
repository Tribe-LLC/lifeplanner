import type { Metadata } from 'next';
import Link from 'next/link';

export const metadata: Metadata = { title: 'Overview | Admin' };

// The site is a static export with no server, so nothing here may use the
// Supabase service-role key. Usage numbers live in PostHog and Supabase.
const LINKS = [
  { href: '/admin/coaches', label: 'Coaches', text: 'Edit the built-in coaches.' },
  { href: '/admin/prompts', label: 'Prompts', text: 'Edit the system prompts the app fetches.' },
  { href: 'https://us.posthog.com/project/293959', label: 'PostHog', text: 'Active users, retention, funnels.' },
  {
    href: 'https://supabase.com/dashboard/project/rkdggdfabwgukspylybu',
    label: 'Supabase',
    text: 'Users, tables and Edge Function logs.',
  },
];

export default function AdminOverviewPage() {
  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Overview</h1>
      <div className="grid sm:grid-cols-2 gap-4">
        {LINKS.map((l) => (
          <Link
            key={l.href}
            href={l.href}
            className="block rounded-xl border border-gray-200 dark:border-white/10 p-5 hover:border-indigo-400 transition-colors"
          >
            <span className="block font-semibold">{l.label}</span>
            <span className="block mt-1 text-sm text-gray-500 dark:text-white/50">{l.text}</span>
          </Link>
        ))}
      </div>
    </div>
  );
}
