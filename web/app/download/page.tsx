'use client';

import { useEffect } from 'react';

const APP_STORE = 'https://apps.apple.com/app/life-planner-ai-coach/id6745726864';
const PLAY_STORE = 'https://play.google.com/store/apps/details?id=az.tribe.lifeplanner';
const FALLBACK = '/';

export default function DownloadRedirect() {
  useEffect(() => {
    const ua = navigator.userAgent || '';

    if (/iPad|iPhone|iPod/.test(ua)) {
      window.location.href = APP_STORE;
    } else if (/Android/i.test(ua)) {
      window.location.href = PLAY_STORE;
    } else {
      window.location.href = FALLBACK;
    }
  }, []);

  return (
    <div className="min-h-screen flex items-center justify-center dark:bg-[#0a0a0f] bg-gray-50">
      <p className="text-lg dark:text-white/60 text-gray-500">Redirecting to store...</p>
    </div>
  );
}
