import Link from 'next/link';
import Image from 'next/image';
import { SmartAppBanner } from '@/components/lifeplanner/smart-app-banner';
import { PlayableApp } from '@/components/lifeplanner/playable-app';

const APP_STORE_URL = 'https://apps.apple.com/app/life-planner-ai-coach/id6745726864';
const PLAY_STORE_URL = 'https://play.google.com/store/apps/details?id=az.tribe.lifeplanner';

function AppleLogo() {
  return (
    <svg viewBox="0 0 24 24" className="w-5 h-5" fill="currentColor" aria-hidden="true">
      <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z" />
    </svg>
  );
}

function PlayLogo() {
  return (
    <svg viewBox="0 0 24 24" className="w-5 h-5" fill="currentColor" aria-hidden="true">
      <path d="M3.6 2.2c-.3.3-.4.7-.4 1.2v17.2c0 .5.1.9.4 1.2l.1.1 9.6-9.6v-.2L3.7 2.1l-.1.1zm12.9 12.9-3.2-3.2v-.2l3.2-3.2.1.1 3.8 2.2c1.1.6 1.1 1.6 0 2.2l-3.8 2.2-.1-.1zm.1.1L13.3 12l-9.7 9.7c.4.4.9.4 1.6.1l11.4-6.6m0-6.4L5.2 2.2C4.5 1.8 4 1.9 3.6 2.3l9.7 9.7 3.3-3.3z" />
    </svg>
  );
}

function StoreButtons() {
  const base =
    'inline-flex items-center gap-3 rounded-2xl px-5 py-3 font-medium transition-colors';
  return (
    <div className="flex flex-wrap gap-3">
      <a
        href={APP_STORE_URL}
        className={`${base} bg-lp-ink text-white hover:bg-black dark:bg-white dark:text-lp-ink dark:hover:bg-lp-tint`}
      >
        <AppleLogo />
        <span className="text-left leading-tight">
          <span className="block text-[11px] opacity-70">Download on the</span>
          App Store
        </span>
      </a>
      <a
        href={PLAY_STORE_URL}
        className={`${base} bg-lp-ink text-white hover:bg-black dark:bg-white dark:text-lp-ink dark:hover:bg-lp-tint`}
      >
        <PlayLogo />
        <span className="text-left leading-tight">
          <span className="block text-[11px] opacity-70">Get it on</span>
          Google Play
        </span>
      </a>
    </div>
  );
}

export default function LifePlannerPage() {
  return (
    <div className="min-h-screen bg-lp-bg text-lp-ink dark:bg-lp-bg-dark dark:text-lp-ink-dark">
      <SmartAppBanner />

      <header className="max-w-6xl mx-auto px-4 sm:px-6 py-5 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-3">
          <Image src="/lifeplanner/icon-light.png" alt="" width={36} height={36} className="rounded-xl dark:hidden" />
          <Image src="/lifeplanner/icon-dark.png" alt="" width={36} height={36} className="rounded-xl hidden dark:block" />
          <span className="text-lg font-bold">Life Planner</span>
        </Link>
        <nav className="flex items-center gap-5 text-sm text-lp-muted dark:text-lp-muted-dark">
          <Link href="/blog" className="hover:text-lp-primary">Blog</Link>
          <Link href="/support" className="hover:text-lp-primary">Support</Link>
        </nav>
      </header>

      <main>
        <section className="max-w-6xl mx-auto px-4 sm:px-6 pt-8 pb-16">
          <PlayableApp
            intro={
              <div className="mb-8">
                <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold leading-[1.08] tracking-tight">
                  Your whole life,{' '}
                  <span className="text-lp-primary dark:text-lp-primary-dark">one calm planner.</span>
                </h1>
                <p className="mt-6 text-lg text-lp-muted dark:text-lp-muted-dark max-w-xl">
                  Habits, money, travel, meals, study and more, in one place. Pick the areas that matter to you, and a coach that sees your whole week helps you keep going.
                </p>
                <div className="mt-8">
                  <StoreButtons />
                </div>
                <p className="mt-4 text-sm text-lp-muted dark:text-lp-muted-dark">Free on iPhone and Android.</p>
              </div>
            }
          />
        </section>

        <section className="max-w-6xl mx-auto px-4 sm:px-6 pb-20">
          <div className="rounded-3xl bg-gradient-to-br from-lp-primary to-lp-violet px-6 py-12 sm:px-12 text-white flex flex-col md:flex-row md:items-center md:justify-between gap-8">
            <div>
              <h2 className="text-3xl sm:text-4xl font-bold tracking-tight">Start with one area today.</h2>
              <p className="mt-3 text-white/80 max-w-md">
                Pick what matters right now. Add more when you are ready. Your data stays yours.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              <a href={APP_STORE_URL} className="inline-flex items-center gap-2 rounded-2xl bg-white px-5 py-3 font-medium text-lp-ink hover:bg-lp-tint">
                <AppleLogo /> App Store
              </a>
              <a href={PLAY_STORE_URL} className="inline-flex items-center gap-2 rounded-2xl bg-white px-5 py-3 font-medium text-lp-ink hover:bg-lp-tint">
                <PlayLogo /> Google Play
              </a>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-lp-line dark:border-lp-line-dark">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8 flex flex-col sm:flex-row gap-4 sm:items-center sm:justify-between text-sm text-lp-muted dark:text-lp-muted-dark">
          <p>&copy; {new Date().getFullYear()} Tribe Solutions MMC</p>
          <div className="flex flex-wrap gap-5">
            <Link href="/support" className="hover:text-lp-primary">Support</Link>
            <Link href="/privacy-policy" className="hover:text-lp-primary">Privacy</Link>
            <Link href="/terms" className="hover:text-lp-primary">Terms</Link>
            <Link href="/delete-account" className="hover:text-lp-primary">Delete account</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
