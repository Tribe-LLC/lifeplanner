'use client';

import { useEffect, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { supabase } from '@/lib/supabase';

const ADMIN_EMAIL = 'kamran@identityailabs.com';

const NAV = [
  { href: '/admin', label: 'Analytics' },
  { href: '/admin/coaches', label: 'Coaches' },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [checking, setChecking] = useState(true);
  const [email, setEmail] = useState<string | null>(null);

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      const userEmail = data.session?.user?.email ?? null;
      if (!data.session) {
        router.replace('/chat');
        return;
      }
      if (userEmail !== ADMIN_EMAIL) {
        router.replace('/');
        return;
      }
      setEmail(userEmail);
      setChecking(false);
    });
  }, [router]);

  if (checking) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-950 text-gray-400 text-sm">
        Authenticating…
      </div>
    );
  }

  return (
    <div className="flex h-screen bg-gray-950 text-gray-100">
      {/* Sidebar */}
      <aside className="w-52 flex-shrink-0 border-r border-gray-800 flex flex-col">
        <div className="px-5 py-5 border-b border-gray-800">
          <p className="text-xs font-semibold text-indigo-400 uppercase tracking-widest">Admin</p>
          <p className="text-sm font-bold mt-1">Life Planner</p>
        </div>
        <nav className="flex-1 px-3 py-4 space-y-1">
          {NAV.map(({ href, label }) => {
            const active = href === '/admin' ? pathname === href : pathname.startsWith(href);
            return (
              <Link
                key={href}
                href={href}
                className={`block px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  active
                    ? 'bg-indigo-600 text-white'
                    : 'text-gray-400 hover:bg-gray-800 hover:text-white'
                }`}
              >
                {label}
              </Link>
            );
          })}
        </nav>
        <div className="px-4 py-4 border-t border-gray-800">
          <p className="text-xs text-gray-500 truncate mb-2">{email}</p>
          <button
            onClick={() => supabase.auth.signOut().then(() => router.replace('/chat'))}
            className="text-xs text-gray-500 hover:text-red-400 transition-colors"
          >
            Sign out
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto">{children}</main>
    </div>
  );
}
