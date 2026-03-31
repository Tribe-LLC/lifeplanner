'use client';

import Link from 'next/link';
import Image from 'next/image';
import { motion } from 'framer-motion';
import { Download } from 'lucide-react';
import { ThemeToggle } from '@/components/landing/theme-toggle';
import { SmartAppBanner } from '@/components/lifeplanner/smart-app-banner';
import { MetaPixel } from '@/components/lifeplanner/meta-pixel';

const features = [
  {
    title: "Goals & Milestones",
    description: "Set SMART goals with AI, track progress with milestones, and visualize dependencies on an interactive graph.",
    screenshot: "/lifeplanner/screenshots/en_02_goals.png",
  },
  {
    title: "Habit Tracker",
    description: "Build daily routines with streak tracking, XP rewards, and habit-to-goal linking to stay consistent.",
    screenshot: "/lifeplanner/screenshots/en_03_habits.png",
  },
  {
    title: "Journal & Mood",
    description: "Reflect daily with mood tracking, tags, and an AI-assisted journal wizard with built-in prompt categories.",
    screenshot: "/lifeplanner/screenshots/en_04_journal.png",
  },
  {
    title: "AI Coach",
    description: "Chat with 7 unique coach personas or create your own. Get actionable advice based on your goals and progress.",
    screenshot: "/lifeplanner/screenshots/en_05_ai_coach.png",
  },
  {
    title: "Focus Timer",
    description: "Pomodoro sessions with ambient sounds and visual themes. Tie focus time directly to your goal milestones.",
    screenshot: "/lifeplanner/screenshots/en_06_focus_timer.png",
  },
];

const highlights = [
  "AI-powered SMART goal generation",
  "7 life categories (Career, Financial, Physical, Social, Emotional, Spiritual, Family)",
  "29 badges across 7 categories with XP & leveling",
  "Weekly, monthly & quarterly AI reviews",
  "Life Balance analyzer with trend tracking",
  "Offline-first with optional cloud sync",
  "28 goal templates to get started fast",
  "Full data export & import",
];

export default function LifePlannerPage() {
  return (
    <div className="min-h-screen dark:bg-[#0a0a0f] bg-gray-50 dark:text-white text-gray-900 transition-colors duration-500">
      <MetaPixel />
      <SmartAppBanner />

      {/* Animated Background */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-[-20%] right-[-10%] w-[60vw] h-[60vw] dark:bg-emerald-600/20 bg-emerald-300/30 rounded-full blur-[150px] animate-pulse" />
        <div className="absolute bottom-[-20%] left-[-10%] w-[50vw] h-[50vw] dark:bg-teal-600/20 bg-teal-300/30 rounded-full blur-[150px] animate-pulse" style={{ animationDelay: '1s' }} />
      </div>

      {/* Navigation */}
      <motion.nav
        initial={{ y: -20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        className="fixed top-0 left-0 right-0 z-50 p-4"
      >
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl overflow-hidden">
              <Image src="/lifeplanner/icon-dark.png" alt="Life Planner" width={32} height={32} className="hidden dark:block" />
              <Image src="/lifeplanner/icon-light.png" alt="Life Planner" width={32} height={32} className="dark:hidden" />
            </div>
            <span className="font-bold bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">
              Life Planner
            </span>
          </div>
          <ThemeToggle />
        </div>
      </motion.nav>

      {/* Hero Section */}
      <section className="relative z-10 min-h-screen flex items-center pt-20">
        <div className="container mx-auto px-6">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            {/* Left Content */}
            <motion.div
              initial={{ opacity: 0, x: -30 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6 }}
            >
              {/* App Icon & Name */}
              <div className="flex items-center gap-4 mb-8">
                <div className="w-16 h-16 rounded-2xl overflow-hidden shadow-lg shadow-emerald-500/25">
                  <Image
                    src="/lifeplanner/icon-dark.png"
                    alt="Life Planner icon"
                    width={64}
                    height={64}
                    className="hidden dark:block"
                  />
                  <Image
                    src="/lifeplanner/icon-light.png"
                    alt="Life Planner icon"
                    width={64}
                    height={64}
                    className="dark:hidden"
                  />
                </div>
                <div>
                  <h1 className="text-3xl font-bold bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">
                    Life Planner
                  </h1>
                  <p className="dark:text-white/60 text-gray-500 text-sm">AI Coach</p>
                </div>
              </div>

              {/* Headline */}
              <h2 className="text-5xl xl:text-6xl font-bold leading-tight mb-6">
                Your goals, habits &
                <span className="block bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">
                  AI-powered growth
                </span>
              </h2>

              <p className="text-xl dark:text-white/60 text-gray-600 mb-8 max-w-lg">
                Set goals, build habits, journal your thoughts, and get personalized AI coaching — all in one beautifully designed app.
              </p>

              {/* Download Buttons */}
              <div className="flex flex-col sm:flex-row gap-4 mb-8">
                <motion.a
                  href="https://apps.apple.com/app/life-planner-ai-coach/id6745726864"
                  target="_blank"
                  rel="noopener noreferrer"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  className="group relative overflow-hidden px-8 py-4 bg-gradient-to-r from-emerald-500 to-teal-600 text-white rounded-2xl font-semibold text-lg inline-flex items-center gap-3 shadow-lg shadow-emerald-500/25"
                >
                  <svg className="w-6 h-6" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M17.05 20.28c-.98.95-2.05.8-3.08.35-1.09-.46-2.09-.48-3.24 0-1.44.62-2.2.44-3.06-.35C2.79 15.25 3.51 7.59 9.05 7.31c1.35.07 2.29.74 3.08.8 1.18-.24 2.31-.93 3.57-.84 1.51.12 2.65.72 3.4 1.8-3.12 1.87-2.38 5.98.48 7.13-.57 1.5-1.31 2.99-2.53 4.08zM12.03 7.25c-.15-2.23 1.66-4.07 3.74-4.25.29 2.58-2.34 4.5-3.74 4.25z"/>
                  </svg>
                  <div className="text-left">
                    <div className="text-xs opacity-80">Download on the</div>
                    <div className="text-base font-bold -mt-0.5">App Store</div>
                  </div>
                  <div className="absolute inset-0 bg-white/20 translate-x-[-100%] group-hover:translate-x-[100%] transition-transform duration-500" />
                </motion.a>

                <motion.a
                  href="https://play.google.com/store/apps/details?id=az.tribe.lifeplanner"
                  target="_blank"
                  rel="noopener noreferrer"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  className="group px-8 py-4 dark:bg-white/10 bg-gray-900 dark:text-white text-white rounded-2xl font-semibold text-lg inline-flex items-center gap-3 border dark:border-white/20 border-transparent dark:hover:bg-white/20 hover:bg-gray-800 transition-all"
                >
                  <svg className="w-6 h-6" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M3.609 1.814L13.792 12 3.61 22.186a.996.996 0 0 1-.61-.92V2.734a1 1 0 0 1 .609-.92zm10.89 10.893l2.302 2.302-10.937 6.333 8.635-8.635zm3.199-3.198l2.807 1.626a1 1 0 0 1 0 1.73l-2.808 1.626L15.206 12l2.492-2.491zM5.864 2.658L16.8 8.99l-2.302 2.302-8.634-8.634z"/>
                  </svg>
                  <div className="text-left">
                    <div className="text-xs opacity-80">Get it on</div>
                    <div className="text-base font-bold -mt-0.5">Google Play</div>
                  </div>
                </motion.a>
              </div>
            </motion.div>

            {/* Right - App Screenshot */}
            <motion.div
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
              className="relative flex justify-center"
            >
              <div className="relative">
                <div className="absolute inset-0 bg-gradient-to-r from-emerald-500 to-teal-600 rounded-[3rem] blur-3xl opacity-20" />
                <div className="relative w-72 rounded-[2rem] overflow-hidden shadow-2xl shadow-emerald-900/30">
                  <Image
                    src="/lifeplanner/screenshots/en_01_home.png"
                    alt="Life Planner home screen"
                    width={320}
                    height={640}
                    className="w-full h-auto"
                    priority
                  />
                </div>
              </div>
            </motion.div>
          </div>
        </div>
      </section>

      {/* Features with Screenshots */}
      <section className="relative z-10 py-24">
        <div className="container mx-auto px-6">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="text-center mb-20"
          >
            <h2 className="text-4xl md:text-5xl font-bold mb-6">
              Everything you need to
              <span className="block bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">level up your life</span>
            </h2>
            <p className="text-xl dark:text-white/60 text-gray-600 max-w-2xl mx-auto">
              Goals, habits, journaling, AI coaching, and focus tools — designed to work together.
            </p>
          </motion.div>

          <div className="space-y-32">
            {features.map((feature, index) => (
              <motion.div
                key={feature.title}
                initial={{ opacity: 0, y: 40 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: "-100px" }}
                transition={{ duration: 0.5 }}
                className={`grid md:grid-cols-2 gap-12 items-center ${index % 2 === 1 ? 'md:direction-rtl' : ''}`}
              >
                <div className={index % 2 === 1 ? 'md:order-2' : ''}>
                  <h3 className="text-3xl md:text-4xl font-bold mb-4">{feature.title}</h3>
                  <p className="text-lg dark:text-white/60 text-gray-600 leading-relaxed">
                    {feature.description}
                  </p>
                </div>
                <div className={`flex justify-center ${index % 2 === 1 ? 'md:order-1' : ''}`}>
                  <div className="relative">
                    <div className="absolute inset-0 bg-gradient-to-r from-emerald-500/20 to-teal-600/20 rounded-[2rem] blur-2xl" />
                    <div className="relative w-60 rounded-[1.5rem] overflow-hidden shadow-xl shadow-black/20">
                      <Image
                        src={feature.screenshot}
                        alt={feature.title}
                        width={280}
                        height={560}
                        className="w-full h-auto"
                      />
                    </div>
                  </div>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* Highlights Grid */}
      <section className="relative z-10 py-24">
        <div className="container mx-auto px-6">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="text-center mb-16"
          >
            <h2 className="text-4xl md:text-5xl font-bold mb-6">
              And <span className="bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">so much more</span>
            </h2>
          </motion.div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 max-w-5xl mx-auto">
            {highlights.map((item, index) => (
              <motion.div
                key={item}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: index * 0.05 }}
                className="p-5 rounded-2xl dark:bg-white/5 bg-white border dark:border-white/10 border-gray-200 backdrop-blur-sm"
              >
                <div className="w-2 h-2 rounded-full bg-emerald-500 mb-3" />
                <p className="text-sm font-medium dark:text-white/80 text-gray-700">{item}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* Gamification Section */}
      <section className="relative z-10 py-16">
        <div className="container mx-auto px-6">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="max-w-3xl mx-auto p-8 rounded-3xl dark:bg-emerald-500/10 bg-emerald-50 border dark:border-emerald-500/20 border-emerald-200"
          >
            <div className="text-center">
              <div className="text-4xl mb-4">🎮</div>
              <h3 className="text-2xl font-bold mb-3 dark:text-emerald-200 text-emerald-800">Stay Motivated with Gamification</h3>
              <p className="dark:text-emerald-200/70 text-emerald-700">
                Earn XP for completing goals, habits, and journal entries. Level up from Novice to Life Master,
                unlock 29 badges, and take on daily, weekly, and monthly challenges. Your growth journey just got fun.
              </p>
            </div>
          </motion.div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="relative z-10 py-32">
        <div className="container mx-auto px-6 text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
          >
            <h2 className="text-4xl md:text-6xl font-bold mb-6">
              Start planning your
              <span className="block bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">best life today</span>
            </h2>
            <p className="text-xl dark:text-white/60 text-gray-600 mb-12 max-w-2xl mx-auto">
              Free to use. No account required. Your data stays on your device.
            </p>
            <div className="flex flex-col sm:flex-row gap-4 justify-center">
              <motion.a
                href="https://apps.apple.com/app/life-planner-ai-coach/id6745726864"
                target="_blank"
                rel="noopener noreferrer"
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="inline-flex items-center gap-3 px-10 py-5 bg-gradient-to-r from-emerald-500 to-teal-600 text-white rounded-full font-bold text-xl shadow-lg shadow-emerald-500/25"
              >
                <Download className="w-6 h-6" />
                App Store
              </motion.a>
              <motion.a
                href="https://play.google.com/store/apps/details?id=az.tribe.lifeplanner"
                target="_blank"
                rel="noopener noreferrer"
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="inline-flex items-center gap-3 px-10 py-5 dark:bg-white/10 bg-gray-900 text-white rounded-full font-bold text-xl border dark:border-white/20 border-transparent dark:hover:bg-white/20 hover:bg-gray-800 transition-all"
              >
                <Download className="w-6 h-6" />
                Google Play
              </motion.a>
            </div>
          </motion.div>
        </div>
      </section>

      {/* Footer */}
      <footer className="relative z-10 border-t dark:border-white/10 border-gray-200">
        <div className="container mx-auto px-6 py-12">
          <div className="flex flex-col md:flex-row items-center justify-between gap-6">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl overflow-hidden">
                <Image src="/lifeplanner/icon-dark.png" alt="Life Planner" width={40} height={40} className="hidden dark:block" />
                <Image src="/lifeplanner/icon-light.png" alt="Life Planner" width={40} height={40} className="dark:hidden" />
              </div>
              <div>
                <span className="text-xl font-bold bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">
                  Life Planner
                </span>
                <p className="dark:text-white/40 text-gray-500 text-sm">by tribe.az</p>
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-center gap-6 text-sm">
              <Link href="/blog" className="dark:text-white/60 text-gray-600 hover:text-emerald-500 transition-colors">
                Blog
              </Link>
              <Link href="/privacy-policy" className="dark:text-white/60 text-gray-600 hover:text-emerald-500 transition-colors">
                Privacy Policy
              </Link>
              <Link href="/terms" className="dark:text-white/60 text-gray-600 hover:text-emerald-500 transition-colors">
                Terms
              </Link>
              <Link href="/delete-account" className="dark:text-white/60 text-gray-600 hover:text-emerald-500 transition-colors">
                Delete Account
              </Link>
              <a href="mailto:support@tribe.az" className="dark:text-white/60 text-gray-600 hover:text-emerald-500 transition-colors">
                Contact
              </a>
            </div>
          </div>

          <div className="mt-8 pt-8 border-t dark:border-white/10 border-gray-200 text-center dark:text-white/40 text-gray-500 text-sm">
            <p>&copy; {new Date().getFullYear()} tribe.az. All rights reserved.</p>
          </div>
        </div>
      </footer>
    </div>
  );
}
