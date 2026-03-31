'use client';

import { useState, useEffect } from 'react';
import Image from 'next/image';
import { X } from 'lucide-react';

const DISMISS_KEY = 'smart-app-banner-dismissed';
const DISMISS_DURATION = 24 * 60 * 60 * 1000; // 24 hours

type Platform = 'ios' | 'android' | 'desktop';

function detectPlatform(): Platform {
  const ua = navigator.userAgent;
  if (/iPad|iPhone|iPod/.test(ua)) return 'ios';
  if (/Android/.test(ua)) return 'android';
  return 'desktop';
}

const storeLinks: Record<'ios' | 'android', { url: string; label: string }> = {
  ios: {
    url: 'https://apps.apple.com/app/life-planner-ai-coach/id6745726864',
    label: 'App Store',
  },
  android: {
    url: 'https://play.google.com/store/apps/details?id=az.tribe.lifeplanner',
    label: 'Google Play',
  },
};

export function SmartAppBanner() {
  const [visible, setVisible] = useState(false);
  const [platform, setPlatform] = useState<Platform>('desktop');

  useEffect(() => {
    const dismissed = localStorage.getItem(DISMISS_KEY);
    if (dismissed && Date.now() - Number(dismissed) < DISMISS_DURATION) return;

    const detected = detectPlatform();
    if (detected === 'desktop') return;

    setPlatform(detected);
    setVisible(true);
  }, []);

  function dismiss() {
    localStorage.setItem(DISMISS_KEY, String(Date.now()));
    setVisible(false);
  }

  if (!visible || platform === 'desktop') return null;

  const store = storeLinks[platform];

  return (
    <div className="fixed top-0 left-0 right-0 z-[100] bg-white/95 dark:bg-[#0a0a0f]/95 backdrop-blur-md border-b border-gray-200 dark:border-white/10 safe-area-top">
      <div className="flex items-center gap-3 px-4 py-3">
        <button
          onClick={dismiss}
          className="shrink-0 p-1 rounded-full text-gray-400 hover:text-gray-600 dark:text-white/40 dark:hover:text-white/70 transition-colors"
          aria-label="Close banner"
        >
          <X className="w-4 h-4" />
        </button>

        <div className="w-10 h-10 rounded-xl overflow-hidden shrink-0 shadow-sm">
          <Image
            src="/lifeplanner/icon-dark.png"
            alt="Life Planner"
            width={40}
            height={40}
            className="hidden dark:block"
          />
          <Image
            src="/lifeplanner/icon-light.png"
            alt="Life Planner"
            width={40}
            height={40}
            className="dark:hidden"
          />
        </div>

        <div className="flex-1 min-w-0">
          <p className="font-semibold text-sm text-gray-900 dark:text-white truncate">
            Life Planner
          </p>
          <p className="text-xs text-gray-500 dark:text-white/50">
            AI Coach &middot; Goals &middot; Habits
          </p>
        </div>

        <a
          href={store.url}
          target="_blank"
          rel="noopener noreferrer"
          className="shrink-0 px-4 py-1.5 bg-gradient-to-r from-emerald-500 to-teal-600 text-white text-sm font-semibold rounded-full"
        >
          {store.label}
        </a>
      </div>
    </div>
  );
}
