import Link from 'next/link';
import Image from 'next/image';
import { ArrowLeft } from 'lucide-react';

interface LegalLayoutProps {
  title: string;
  lastUpdated: string;
  backLink: string;
  backLabel: string;
  children: React.ReactNode;
  // Kept for existing callers; every page now uses the app palette.
  accentColor?: 'blue' | 'rose' | 'emerald';
}

export function LegalLayout({ title, lastUpdated, backLink, backLabel, children }: LegalLayoutProps) {
  return (
    <div className="min-h-screen bg-lp-bg text-lp-ink dark:bg-lp-bg-dark dark:text-lp-ink-dark">
      <header className="max-w-4xl mx-auto px-4 sm:px-6 py-5 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-3">
          <Image src="/lifeplanner/icon-light.png" alt="" width={32} height={32} className="rounded-xl dark:hidden" />
          <Image src="/lifeplanner/icon-dark.png" alt="" width={32} height={32} className="rounded-xl hidden dark:block" />
          <span className="font-bold">Life Planner</span>
        </Link>
        <Link
          href={backLink}
          className="inline-flex items-center gap-2 text-sm text-lp-muted dark:text-lp-muted-dark hover:text-lp-primary"
        >
          <ArrowLeft className="w-4 h-4" />
          {backLabel}
        </Link>
      </header>

      <main className="px-4 sm:px-6 pb-16">
        <div className="max-w-4xl mx-auto rounded-3xl bg-lp-surface dark:bg-lp-surface-dark border border-lp-line dark:border-lp-line-dark p-6 sm:p-12">
          <div className="mb-10">
            <h1 className="text-4xl md:text-5xl font-bold tracking-tight">{title}</h1>
            <p className="mt-3 text-lp-muted dark:text-lp-muted-dark">Last updated: {lastUpdated}</p>
          </div>
          <div className="legal-content">{children}</div>
        </div>
      </main>
    </div>
  );
}

export function LegalSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mb-10">
      <h2 className="text-xl md:text-2xl font-bold mb-4 pb-3 border-b border-lp-line dark:border-lp-line-dark">
        {title}
      </h2>
      <div className="text-lp-muted dark:text-lp-muted-dark space-y-4 leading-relaxed">
        {children}
      </div>
    </div>
  );
}

export function LegalHighlight({ title, children }: { title: string; children: React.ReactNode; accentColor?: 'blue' | 'rose' | 'emerald' }) {
  return (
    <div className="bg-lp-tint dark:bg-lp-tint-dark border-l-4 border-lp-primary p-6 rounded-r-xl my-8">
      <h2 className="text-xl font-bold mb-3">{title}</h2>
      <div className="text-lp-muted dark:text-lp-muted-dark">{children}</div>
    </div>
  );
}

export function LegalLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <Link href={href} className="text-lp-primary hover:opacity-80 hover:underline transition-colors">
      {children}
    </Link>
  );
}

export function LegalFooter({ links }: { links: { label: string; href: string }[] }) {
  return (
    <div className="mt-12 pt-8 border-t border-lp-line dark:border-lp-line-dark flex flex-wrap justify-center gap-6 text-sm">
      {links.map((link) => (
        <Link
          key={link.href}
          href={link.href}
          className="text-lp-primary hover:opacity-80 hover:underline transition-colors"
        >
          {link.label}
        </Link>
      ))}
    </div>
  );
}
