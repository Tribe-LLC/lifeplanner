'use client';

import { useState, type ReactNode } from 'react';
import Image from 'next/image';

type ScreenId = 'today' | 'checkin' | 'life' | 'coach' | 'money' | 'travel' | 'meals';

// A hotspot is a tappable area, in percent of the full 1080x2400 capture.
// The images have the status bar and gesture bar (63px each) cut off, so
// toImage() maps those numbers onto the cropped picture.
type Hotspot = { label: string; to: ScreenId; x: number; y: number; w: number; h: number };

const CAPTURE_H = 2400;
const BAR_H = 63;
const IMAGE_H = CAPTURE_H - 2 * BAR_H;

function toImage(h: Hotspot) {
  const top = ((h.y / 100) * CAPTURE_H - BAR_H) / IMAGE_H * 100;
  const height = (h.h / 100) * CAPTURE_H / IMAGE_H * 100;
  return { left: `${h.x}%`, top: `${Math.max(0, top)}%`, width: `${h.w}%`, height: `${height}%` };
}

const TABS: Hotspot[] = [
  { label: 'Today tab', to: 'today', x: 0, y: 90, w: 33, h: 8 },
  { label: 'Life tab', to: 'life', x: 33, y: 90, w: 34, h: 8 },
  { label: 'Coach tab', to: 'coach', x: 67, y: 90, w: 33, h: 8 },
];

const BACK_TO_LIFE: Hotspot = { label: 'Back to Life', to: 'life', x: 1, y: 3, w: 20, h: 6 };

const SCREENS: Record<ScreenId, { alt: string; hotspots: Hotspot[] }> = {
  today: {
    alt: 'Today screen with a coach note and the evening check-in',
    hotspots: [{ label: 'Check in on habits', to: 'checkin', x: 7, y: 51, w: 86, h: 7 }, ...TABS],
  },
  checkin: {
    alt: 'Habit check-in card: swipe right for done, left for not today',
    hotspots: [{ label: 'Close check-in', to: 'today', x: 2, y: 3, w: 15, h: 7 }],
  },
  life: {
    alt: 'Life screen with your areas: habits, fitness, money, travel, study, meals',
    hotspots: [
      { label: 'Open Money', to: 'money', x: 4, y: 36, w: 45, h: 21 },
      { label: 'Open Travel', to: 'travel', x: 51, y: 36, w: 45, h: 20 },
      { label: 'Open Meals', to: 'meals', x: 51, y: 59, w: 45, h: 19 },
      ...TABS,
    ],
  },
  coach: { alt: 'Coach chat with suggestions you can tap', hotspots: TABS },
  money: { alt: 'Money page with a budget, bills and a savings plan', hotspots: [BACK_TO_LIFE] },
  travel: { alt: 'Travel page to plan a trip with budget and packing', hotspots: [BACK_TO_LIFE] },
  meals: { alt: 'Meals page with today, water and the week plan', hotspots: [BACK_TO_LIFE] },
};

const FEATURES: { id: ScreenId; title: string; text: string }[] = [
  { id: 'today', title: 'Today', text: 'What matters now, a note from your coach, and a calm wrap-up at night.' },
  { id: 'checkin', title: 'Check in', text: 'Swipe through your habits. Not today never breaks a streak.' },
  { id: 'life', title: 'Life', text: 'Every area of your life on one screen. Only the ones you pick.' },
  { id: 'money', title: 'Money', text: 'One budget, bills that remind you, and savings plans that keep pace.' },
  { id: 'travel', title: 'Travel', text: 'Plan a trip with a budget, a packing list and the weather for each day.' },
  { id: 'coach', title: 'Coach', text: 'Ask anything. The coach sees your week across all your areas.' },
];

const HINTS: Partial<Record<ScreenId, string>> = {
  today: 'Tap the app to look around.',
  checkin: 'Tap the close button to go back.',
  life: 'Tap an area, or a tab at the bottom.',
  money: 'Tap Life at the top to go back.',
  travel: 'Tap Life at the top to go back.',
  meals: 'Tap Life at the top to go back.',
};

export function PlayableApp({ intro }: { intro: ReactNode }) {
  const [screen, setScreen] = useState<ScreenId>('today');
  const [touched, setTouched] = useState(false);

  const go = (to: ScreenId) => {
    setScreen(to);
    setTouched(true);
  };

  return (
    <div className="grid md:grid-cols-[1fr_auto] md:grid-rows-[auto_1fr] gap-10 md:gap-16 items-start">
      <div className="order-1 md:col-start-1 md:row-start-1">{intro}</div>

      <ul className="grid sm:grid-cols-2 gap-2 order-3 md:col-start-1 md:row-start-2">
        {FEATURES.map((f) => {
          const active = f.id === screen;
          return (
            <li key={f.id}>
              <button
                type="button"
                onClick={() => go(f.id)}
                aria-pressed={active}
                className={`w-full h-full text-left rounded-2xl px-4 py-3 transition-colors border ${
                  active
                    ? 'bg-lp-tint border-lp-primary/30 dark:bg-lp-tint-dark dark:border-lp-primary-dark/40'
                    : 'border-transparent hover:bg-lp-tint/60 dark:hover:bg-lp-tint-dark/60'
                }`}
              >
                <span className={`block font-bold ${active ? 'text-lp-primary dark:text-lp-primary-dark' : ''}`}>
                  {f.title}
                </span>
                <span className="block mt-1 text-sm text-lp-muted dark:text-lp-muted-dark">{f.text}</span>
              </button>
            </li>
          );
        })}
      </ul>

      <div className="order-2 md:col-start-2 md:row-start-1 md:row-span-2 md:sticky md:top-6 mx-auto w-[260px] sm:w-[300px]">
        <div className="rounded-[2.25rem] bg-lp-ink p-2 shadow-2xl shadow-lp-primary/20 dark:bg-black">
          <div className="relative overflow-hidden rounded-[1.75rem] aspect-[1080/2274] bg-lp-bg">
            {(Object.keys(SCREENS) as ScreenId[]).map((id) => (
              <Image
                key={id}
                src={`/lifeplanner/screens/${id}.webp`}
                alt={SCREENS[id].alt}
                fill
                sizes="300px"
                priority={id === 'today'}
                loading={id === 'today' ? undefined : 'eager'}
                aria-hidden={id !== screen}
                className={`object-cover transition-opacity duration-300 ${id === screen ? 'opacity-100' : 'opacity-0'}`}
              />
            ))}
            {SCREENS[screen].hotspots.map((h, i) => (
              <button
                key={`${screen}-${i}`}
                type="button"
                aria-label={h.label}
                onClick={() => go(h.to)}
                className="absolute rounded-xl cursor-pointer transition-colors hover:bg-lp-primary/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-lp-primary"
                style={toImage(h)}
              />
            ))}
            {!touched && (
              <span
                aria-hidden="true"
                className="pointer-events-none absolute left-1/2 top-[54.7%] -translate-x-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-white/50 animate-ping"
              />
            )}
          </div>
        </div>
        <p className="mt-4 text-center text-sm text-lp-muted dark:text-lp-muted-dark">
          {HINTS[screen] ?? 'Tap a tab at the bottom to move around.'}
        </p>
      </div>
    </div>
  );
}
