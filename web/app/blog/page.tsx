import Link from 'next/link';
import Image from 'next/image';
import { ArrowLeft, Clock, ArrowRight } from 'lucide-react';
import { getAllPosts } from '@/lib/blog';
import { ThemeToggle } from '@/components/landing/theme-toggle';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Blog — Life Planner | AI Goal Setting, Habits & Personal Growth',
  description: 'Tips and guides on goal setting, habit tracking, journaling, productivity, and personal growth. Learn how to level up your life with AI-powered tools.',
  openGraph: {
    title: 'Life Planner Blog — Goal Setting, Habits & Personal Growth',
    description: 'Tips and guides on goal setting, habit tracking, journaling, productivity, and personal growth.',
    type: 'website',
    url: 'https://lifeplanner.tribe.az/blog',
    siteName: 'Life Planner',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Life Planner Blog — Goal Setting, Habits & Personal Growth',
    description: 'Tips and guides on goal setting, habit tracking, journaling, productivity, and personal growth.',
  },
  alternates: {
    canonical: 'https://lifeplanner.tribe.az/blog',
  },
};

export const revalidate = 3600;

export default async function BlogListPage() {
  const posts = await getAllPosts();

  return (
    <div className="min-h-screen dark:bg-[#0a0a0f] bg-gray-50 dark:text-white text-gray-900 transition-colors duration-500">
      {/* Background */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-[-20%] right-[-10%] w-[60vw] h-[60vw] dark:bg-emerald-600/10 bg-emerald-300/20 rounded-full blur-[150px]" />
        <div className="absolute bottom-[-20%] left-[-10%] w-[50vw] h-[50vw] dark:bg-teal-600/10 bg-teal-300/20 rounded-full blur-[150px]" />
      </div>

      {/* Navigation */}
      <nav className="fixed top-0 left-0 right-0 z-50 p-4">
        <div className="max-w-5xl mx-auto flex items-center justify-between">
          <Link
            href="/"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full dark:bg-white/10 bg-black/5 dark:hover:bg-white/20 hover:bg-black/10 transition-all dark:text-white/80 text-gray-600 backdrop-blur-sm"
          >
            <ArrowLeft className="w-4 h-4" />
            <span className="text-sm font-medium">Life Planner</span>
          </Link>
          <ThemeToggle />
        </div>
      </nav>

      {/* Header */}
      <header className="relative z-10 pt-28 pb-16 px-6">
        <div className="max-w-5xl mx-auto text-center">
          <h1 className="text-4xl md:text-6xl font-bold mb-6">
            The{' '}
            <span className="bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">
              Life Planner
            </span>{' '}
            Blog
          </h1>
          <p className="text-xl dark:text-white/60 text-gray-600 max-w-2xl mx-auto">
            Tips and strategies for goal setting, habit building, journaling, and personal growth.
          </p>
        </div>
      </header>

      {/* Posts Grid */}
      <main className="relative z-10 pb-24 px-6">
        <div className="max-w-5xl mx-auto grid gap-8 md:grid-cols-2 lg:grid-cols-3">
          {posts.map((post) => (
            <Link
              key={post.slug}
              href={`/blog/${post.slug}`}
              className="group relative"
            >
              <div className="absolute -inset-0.5 bg-gradient-to-r from-emerald-500 to-teal-600 rounded-2xl blur opacity-0 group-hover:opacity-20 transition-opacity" />
              <article className="relative h-full p-6 rounded-2xl dark:bg-white/5 bg-white border dark:border-white/10 border-gray-200 backdrop-blur-sm hover:border-emerald-500/50 transition-all">
                <div className="flex items-center gap-2 mb-3">
                  <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-500">
                    {post.category}
                  </span>
                  <span className="flex items-center gap-1 text-xs dark:text-white/40 text-gray-400">
                    <Clock className="w-3 h-3" />
                    {post.readTime}
                  </span>
                </div>
                <h2 className="text-lg font-bold mb-2 group-hover:text-emerald-500 transition-colors">
                  {post.title}
                </h2>
                <p className="text-sm dark:text-white/50 text-gray-500 mb-4 line-clamp-3">
                  {post.description}
                </p>
                <div className="flex items-center justify-between mt-auto">
                  <time className="text-xs dark:text-white/30 text-gray-400">
                    {new Date(post.date).toLocaleDateString('en-US', {
                      year: 'numeric',
                      month: 'long',
                      day: 'numeric',
                    })}
                  </time>
                  <span className="text-emerald-500 text-sm font-medium inline-flex items-center gap-1 group-hover:gap-2 transition-all">
                    Read <ArrowRight className="w-3 h-3" />
                  </span>
                </div>
              </article>
            </Link>
          ))}
        </div>
      </main>

      {/* CTA */}
      <section className="relative z-10 pb-24 px-6">
        <div className="max-w-3xl mx-auto p-8 rounded-3xl dark:bg-emerald-500/10 bg-emerald-50 border dark:border-emerald-500/20 border-emerald-200 text-center">
          <h2 className="text-2xl font-bold mb-3 dark:text-emerald-200 text-emerald-800">
            Ready to level up your life?
          </h2>
          <p className="dark:text-emerald-200/70 text-emerald-700 mb-6">
            Goals, habits, journaling, and AI coaching — all in one app.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <a
              href="https://apps.apple.com/app/life-planner-ai-coach/id6745726864"
              target="_blank"
              rel="noopener noreferrer"
              className="px-6 py-3 bg-gradient-to-r from-emerald-500 to-teal-600 text-white rounded-xl font-semibold text-sm"
            >
              Download for iOS
            </a>
            <a
              href="https://play.google.com/store/apps/details?id=az.tribe.lifeplanner"
              target="_blank"
              rel="noopener noreferrer"
              className="px-6 py-3 dark:bg-white/10 bg-gray-900 text-white rounded-xl font-semibold text-sm border dark:border-white/20 border-transparent"
            >
              Download for Android
            </a>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="relative z-10 border-t dark:border-white/10 border-gray-200">
        <div className="max-w-5xl mx-auto px-6 py-8 flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg overflow-hidden">
              <Image src="/lifeplanner/icon-dark.png" alt="Life Planner" width={32} height={32} className="hidden dark:block" />
              <Image src="/lifeplanner/icon-light.png" alt="Life Planner" width={32} height={32} className="dark:hidden" />
            </div>
            <span className="font-bold bg-gradient-to-r from-emerald-500 to-teal-600 bg-clip-text text-transparent">
              Life Planner
            </span>
          </div>
          <p className="text-sm dark:text-white/40 text-gray-500">
            &copy; {new Date().getFullYear()} tribe.az. All rights reserved.
          </p>
        </div>
      </footer>
    </div>
  );
}
