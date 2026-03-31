import type { Metadata } from 'next'
import { Inter } from 'next/font/google'
import { Analytics } from '@vercel/analytics/next'
import { ThemeProvider } from '@/components/landing/theme-provider'
import { FirebaseAnalytics } from '@/components/firebase-analytics'
import './globals.css'

const inter = Inter({ subsets: ['latin'], variable: '--font-sans' })

export const metadata: Metadata = {
  title: {
    default: 'Life Planner — AI-Powered Goals, Habits & Personal Growth App',
    template: '%s — Life Planner',
  },
  description:
    'Set goals, build habits, journal your thoughts, and get personalized AI coaching — all in one beautifully designed app. Free to use, offline-first.',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${inter.variable} font-sans antialiased`}>
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
        >
          {children}
        </ThemeProvider>
        <FirebaseAnalytics />
        <Analytics />
      </body>
    </html>
  )
}
