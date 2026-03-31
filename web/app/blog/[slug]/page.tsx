import Link from 'next/link';
import Image from 'next/image';
import { ArrowLeft, Clock, Calendar } from 'lucide-react';
import { getAllSlugs, getPostBySlug } from '@/lib/blog';
import { ThemeToggle } from '@/components/landing/theme-toggle';
import { notFound } from 'next/navigation';
import type { Metadata } from 'next';

interface Props {
  params: Promise<{ slug: string }>;
}

export async function generateStaticParams() {
  return getAllSlugs().map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const post = getPostBySlug(slug);
  if (!post) return {};

  return {
    title: `${post.title} — Life Planner Blog`,
    description: post.description,
    openGraph: {
      title: post.title,
      description: post.description,
      type: 'article',
      publishedTime: post.date,
      url: `https://lifeplanner.tribe.az/blog/${post.slug}`,
      siteName: 'Life Planner',
      tags: post.tags,
    },
    twitter: {
      card: 'summary_large_image',
      title: post.title,
      description: post.description,
    },
    alternates: {
      canonical: `https://lifeplanner.tribe.az/blog/${post.slug}`,
    },
  };
}

function renderMarkdown(content: string) {
  const lines = content.split('\n');
  const elements: React.ReactNode[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    if (line.startsWith('## ')) {
      elements.push(
        <h2 key={i} className="text-2xl font-bold mt-10 mb-4 dark:text-white text-gray-900">
          {line.slice(3)}
        </h2>
      );
    } else if (line.startsWith('### ')) {
      elements.push(
        <h3 key={i} className="text-xl font-bold mt-8 mb-3 dark:text-white text-gray-900">
          {line.slice(4)}
        </h3>
      );
    } else if (line.startsWith('**') && line.endsWith('**')) {
      elements.push(
        <p key={i} className="font-semibold mt-4 mb-2 dark:text-white/90 text-gray-800">
          {line.slice(2, -2)}
        </p>
      );
    } else if (line.startsWith('- ')) {
      // Collect all list items
      const items: string[] = [];
      while (i < lines.length && lines[i].startsWith('- ')) {
        items.push(lines[i].slice(2));
        i++;
      }
      elements.push(
        <ul key={`ul-${i}`} className="list-disc list-inside space-y-1 my-4 dark:text-white/70 text-gray-600">
          {items.map((item, j) => (
            <li key={j}>{item}</li>
          ))}
        </ul>
      );
      continue;
    } else if (line.trim() === '') {
      // skip empty lines
    } else {
      // Regular paragraph — handle inline bold
      const parts = line.split(/(\*\*[^*]+\*\*)/g);
      elements.push(
        <p key={i} className="my-4 dark:text-white/70 text-gray-600 leading-relaxed text-lg">
          {parts.map((part, j) =>
            part.startsWith('**') && part.endsWith('**') ? (
              <strong key={j} className="dark:text-white/90 text-gray-800">
                {part.slice(2, -2)}
              </strong>
            ) : (
              part
            )
          )}
        </p>
      );
    }
    i++;
  }

  return elements;
}

export default async function BlogPostPage({ params }: Props) {
  const { slug } = await params;
  const post = getPostBySlug(slug);

  if (!post) {
    notFound();
  }

  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Article',
    headline: post.title,
    description: post.description,
    datePublished: post.date,
    author: {
      '@type': 'Organization',
      name: 'Life Planner by tribe.az',
      url: 'https://lifeplanner.tribe.az',
    },
    publisher: {
      '@type': 'Organization',
      name: 'tribe.az',
      url: 'https://tribe.az',
    },
    mainEntityOfPage: {
      '@type': 'WebPage',
      '@id': `https://lifeplanner.tribe.az/blog/${post.slug}`,
    },
  };

  return (
    <div className="min-h-screen dark:bg-[#0a0a0f] bg-gray-50 dark:text-white text-gray-900 transition-colors duration-500">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

      {/* Background */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-[-20%] right-[-10%] w-[50vw] h-[50vw] dark:bg-emerald-600/10 bg-emerald-200/20 rounded-full blur-[150px]" />
        <div className="absolute bottom-[-20%] left-[-10%] w-[40vw] h-[40vw] dark:bg-teal-600/10 bg-teal-200/20 rounded-full blur-[150px]" />
      </div>

      {/* Navigation */}
      <nav className="fixed top-0 left-0 right-0 z-50 p-4">
        <div className="max-w-3xl mx-auto flex items-center justify-between">
          <Link
            href="/blog"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full dark:bg-white/10 bg-black/5 dark:hover:bg-white/20 hover:bg-black/10 transition-all dark:text-white/80 text-gray-600 backdrop-blur-sm"
          >
            <ArrowLeft className="w-4 h-4" />
            <span className="text-sm font-medium">All Posts</span>
          </Link>
          <ThemeToggle />
        </div>
      </nav>

      {/* Article */}
      <main className="relative z-10 pt-24 pb-16 px-4">
        <article className="max-w-3xl mx-auto">
          {/* Header */}
          <header className="mb-10">
            <div className="flex items-center gap-3 mb-4">
              <span className="text-sm font-semibold px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-500">
                {post.category}
              </span>
            </div>
            <h1 className="text-3xl md:text-5xl font-bold leading-tight mb-6">
              {post.title}
            </h1>
            <div className="flex items-center gap-4 dark:text-white/40 text-gray-500 text-sm">
              <span className="flex items-center gap-1.5">
                <Calendar className="w-4 h-4" />
                {new Date(post.date).toLocaleDateString('en-US', {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                })}
              </span>
              <span className="flex items-center gap-1.5">
                <Clock className="w-4 h-4" />
                {post.readTime}
              </span>
            </div>
          </header>

          {/* Content */}
          <div className="prose-custom">
            {renderMarkdown(post.content)}
          </div>

          {/* Tags */}
          <div className="mt-12 pt-8 border-t dark:border-white/10 border-gray-200">
            <div className="flex flex-wrap gap-2">
              {post.tags.map((tag) => (
                <span
                  key={tag}
                  className="text-xs px-3 py-1 rounded-full dark:bg-white/5 bg-gray-100 dark:text-white/50 text-gray-500"
                >
                  #{tag}
                </span>
              ))}
            </div>
          </div>

          {/* CTA */}
          <div className="mt-12 p-8 rounded-3xl dark:bg-emerald-500/10 bg-emerald-50 border dark:border-emerald-500/20 border-emerald-200 text-center">
            <h2 className="text-xl font-bold mb-2 dark:text-emerald-200 text-emerald-800">
              Try Life Planner
            </h2>
            <p className="dark:text-emerald-200/70 text-emerald-700 mb-6 text-sm">
              Goals, habits, journaling, and AI coaching — all in one app. Free to use.
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
        </article>
      </main>

      {/* Footer */}
      <footer className="relative z-10 border-t dark:border-white/10 border-gray-200">
        <div className="max-w-3xl mx-auto px-6 py-8 flex flex-col md:flex-row items-center justify-between gap-4">
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
