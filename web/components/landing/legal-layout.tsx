'use client';

import Link from 'next/link';
import { motion } from 'framer-motion';
import { ArrowLeft } from 'lucide-react';
import { ThemeToggle } from './theme-toggle';

interface LegalLayoutProps {
  title: string;
  lastUpdated: string;
  backLink: string;
  backLabel: string;
  children: React.ReactNode;
  accentColor?: 'blue' | 'rose' | 'emerald';
}

export function LegalLayout({
  title,
  lastUpdated,
  backLink,
  backLabel,
  children,
  accentColor = 'blue',
}: LegalLayoutProps) {
  const gradientClass = accentColor === 'rose'
    ? 'from-rose-500 to-purple-600'
    : accentColor === 'emerald'
    ? 'from-emerald-500 to-teal-600'
    : 'from-blue-500 to-cyan-500';

  const accentClass = accentColor === 'rose' ? 'text-rose-500' : accentColor === 'emerald' ? 'text-emerald-500' : 'text-blue-500';
  const borderAccentClass = accentColor === 'rose' ? 'border-rose-500' : accentColor === 'emerald' ? 'border-emerald-500' : 'border-blue-500';

  return (
    <div className="min-h-screen dark:bg-[#0a0a0f] bg-gray-50 dark:text-white text-gray-900 transition-colors duration-500">
      {/* Animated Background */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className={`absolute top-[-20%] right-[-10%] w-[50vw] h-[50vw] ${accentColor === 'rose' ? 'dark:bg-rose-600/10 bg-rose-200/30' : accentColor === 'emerald' ? 'dark:bg-emerald-600/10 bg-emerald-200/30' : 'dark:bg-blue-600/10 bg-blue-200/30'} rounded-full blur-[150px]`} />
        <div className={`absolute bottom-[-20%] left-[-10%] w-[40vw] h-[40vw] ${accentColor === 'rose' ? 'dark:bg-purple-600/10 bg-purple-200/30' : accentColor === 'emerald' ? 'dark:bg-teal-600/10 bg-teal-200/30' : 'dark:bg-cyan-600/10 bg-cyan-200/30'} rounded-full blur-[150px]`} />
      </div>

      {/* Navigation */}
      <motion.nav
        initial={{ y: -20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        className="fixed top-0 left-0 right-0 z-50 p-4"
      >
        <div className="max-w-4xl mx-auto flex items-center justify-between">
          <Link
            href={backLink}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full dark:bg-white/10 bg-black/5 dark:hover:bg-white/20 hover:bg-black/10 transition-all dark:text-white/80 text-gray-600 backdrop-blur-sm"
          >
            <ArrowLeft className="w-4 h-4" />
            <span className="text-sm font-medium">{backLabel}</span>
          </Link>
          <ThemeToggle />
        </div>
      </motion.nav>

      {/* Content */}
      <main className="relative z-10 pt-24 pb-16 px-4">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="max-w-4xl mx-auto"
        >
          <div className="relative">
            {/* Glow */}
            <div className={`absolute -inset-1 bg-gradient-to-r ${gradientClass} rounded-3xl blur-xl opacity-10`} />

            {/* Card */}
            <div className="relative dark:bg-gray-900/80 bg-white/80 backdrop-blur-xl rounded-3xl p-8 md:p-12 border dark:border-white/10 border-gray-200 shadow-xl">
              {/* Header */}
              <div className="text-center mb-12">
                <h1 className={`text-4xl md:text-5xl font-bold mb-4 bg-gradient-to-r ${gradientClass} bg-clip-text text-transparent`}>
                  {title}
                </h1>
                <p className="dark:text-white/50 text-gray-500">
                  Last Updated: {lastUpdated}
                </p>
              </div>

              {/* Content */}
              <div className="legal-content">
                {children}
              </div>
            </div>
          </div>
        </motion.div>
      </main>
    </div>
  );
}

export function LegalSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mb-10">
      <h2 className="text-xl md:text-2xl font-bold mb-4 pb-3 border-b dark:border-white/10 border-gray-200">
        {title}
      </h2>
      <div className="dark:text-white/70 text-gray-600 space-y-4 leading-relaxed">
        {children}
      </div>
    </div>
  );
}

export function LegalHighlight({ title, children, accentColor = 'blue' }: { title: string; children: React.ReactNode; accentColor?: 'blue' | 'rose' | 'emerald' }) {
  const borderClass = accentColor === 'rose' ? 'border-rose-500' : accentColor === 'emerald' ? 'border-emerald-500' : 'border-blue-500';
  const bgClass = accentColor === 'rose' ? 'dark:bg-rose-500/10 bg-rose-50' : accentColor === 'emerald' ? 'dark:bg-emerald-500/10 bg-emerald-50' : 'dark:bg-blue-500/10 bg-blue-50';

  return (
    <div className={`${bgClass} border-l-4 ${borderClass} p-6 rounded-r-xl mt-8`}>
      <h2 className="text-xl font-bold mb-3">{title}</h2>
      <div className="dark:text-white/70 text-gray-600">{children}</div>
    </div>
  );
}

export function LegalLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <Link href={href} className="text-blue-500 hover:text-blue-400 hover:underline transition-colors">
      {children}
    </Link>
  );
}

export function LegalFooter({ links }: { links: { label: string; href: string }[] }) {
  return (
    <div className="mt-12 pt-8 border-t dark:border-white/10 border-gray-200 flex flex-wrap justify-center gap-6 text-sm">
      {links.map((link) => (
        <Link
          key={link.href}
          href={link.href}
          className="text-blue-500 hover:text-blue-400 hover:underline transition-colors"
        >
          {link.label}
        </Link>
      ))}
    </div>
  );
}
