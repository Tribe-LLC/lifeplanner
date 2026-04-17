'use client';

import { useEffect, useState } from 'react';
import { usePathname } from 'next/navigation';
import Link from 'next/link';
import { supabase } from '@/lib/supabase';

const ADMIN_EMAIL = process.env.NEXT_PUBLIC_ADMIN_EMAIL ?? 'admin@lifeplanner.app';

const NAV = [
  { href: '/admin', label: 'Analytics' },
  { href: '/admin/coaches', label: 'Coaches' },
];

type AuthState = 'checking' | 'unauthenticated' | 'wrong_user' | 'authorized';

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [authState, setAuthState] = useState<AuthState>('checking');
  const [email, setEmail] = useState<string | null>(null);

  // Sign-in form state
  const [formEmail, setFormEmail] = useState('');
  const [formPassword, setFormPassword] = useState('');
  const [formError, setFormError] = useState('');
  const [signingIn, setSigningIn] = useState(false);

  const checkSession = () => {
    supabase.auth.getSession().then(({ data }) => {
      const userEmail = data.session?.user?.email ?? null;
      if (!data.session) { setAuthState('unauthenticated'); return; }
      if (userEmail !== ADMIN_EMAIL) { setAuthState('wrong_user'); setEmail(userEmail); return; }
      setEmail(userEmail);
      setAuthState('authorized');
    });
  };

  useEffect(() => { checkSession(); }, []);

  const handleSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    setSigningIn(true);
    setFormError('');
    const { error } = await supabase.auth.signInWithPassword({ email: formEmail, password: formPassword });
    setSigningIn(false);
    if (error) { setFormError(error.message); return; }
    checkSession();
  };

  if (authState === 'checking') {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-950 text-gray-400 text-sm">
        Authenticating…
      </div>
    );
  }

  if (authState === 'unauthenticated' || authState === 'wrong_user') {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-950">
        <div className="w-full max-w-sm">
          <p className="text-xs font-semibold text-indigo-400 uppercase tracking-widest text-center mb-1">Admin</p>
          <p className="text-xl font-bold text-white text-center mb-6">Life Planner</p>
          {authState === 'wrong_user' && (
            <p className="text-xs text-red-400 text-center mb-4">
              {email} is not an admin account.{' '}
              <button onClick={() => supabase.auth.signOut().then(checkSession)} className="underline">Sign out</button>
            </p>
          )}
          <form onSubmit={handleSignIn} className="space-y-3">
            <input
              type="email"
              placeholder="Email"
              value={formEmail}
              onChange={(e) => setFormEmail(e.target.value)}
              required
              className="w-full bg-gray-800 border border-gray-700 rounded-lg px-4 py-2.5 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-indigo-500"
            />
            <input
              type="password"
              placeholder="Password"
              value={formPassword}
              onChange={(e) => setFormPassword(e.target.value)}
              required
              className="w-full bg-gray-800 border border-gray-700 rounded-lg px-4 py-2.5 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-indigo-500"
            />
            {formError && <p className="text-xs text-red-400">{formError}</p>}
            <button
              type="submit"
              disabled={signingIn}
              className="w-full bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-sm font-semibold py-2.5 rounded-lg transition-colors"
            >
              {signingIn ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        </div>
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
            onClick={() => supabase.auth.signOut().then(() => setAuthState('unauthenticated'))}
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
