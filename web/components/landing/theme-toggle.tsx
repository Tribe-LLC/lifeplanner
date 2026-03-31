"use client"

import { useTheme } from "next-themes"
import { useEffect, useState } from "react"
import { Sun, Moon } from "lucide-react"

export function ThemeToggle() {
  const { setTheme, resolvedTheme } = useTheme()
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    setMounted(true)
  }, [])

  if (!mounted) {
    return (
      <div className="flex items-center gap-3">
        <div className="w-16 h-8 rounded-full bg-gradient-to-r from-gray-200 to-gray-300 dark:from-gray-700 dark:to-gray-600" />
      </div>
    )
  }

  const isDark = resolvedTheme === "dark"

  return (
    <button
      onClick={() => setTheme(isDark ? "light" : "dark")}
      className="relative w-16 h-8 rounded-full overflow-hidden"
      aria-label={`Switch to ${isDark ? "light" : "dark"} mode`}
    >
      {/* Light mode background */}
      <div
        className={`
          absolute inset-0 bg-gradient-to-r from-amber-400 to-orange-400
          transition-opacity duration-700 ease-in-out
          ${isDark ? 'opacity-0' : 'opacity-100'}
        `}
      />

      {/* Dark mode background */}
      <div
        className={`
          absolute inset-0 bg-gradient-to-r from-indigo-600 to-purple-600
          transition-opacity duration-700 ease-in-out
          ${isDark ? 'opacity-100' : 'opacity-0'}
        `}
      />

      {/* Glow effect */}
      <div
        className={`
          absolute inset-0 rounded-full transition-shadow duration-700 ease-in-out
          ${isDark
            ? 'shadow-[0_0_20px_rgba(99,102,241,0.5)]'
            : 'shadow-[0_0_20px_rgba(251,191,36,0.5)]'
          }
        `}
      />

      {/* Track inner shadow */}
      <div className="absolute inset-0.5 rounded-full bg-black/10" />

      {/* Stars decoration for dark mode */}
      <div className={`absolute inset-0 transition-opacity duration-700 ease-in-out ${isDark ? 'opacity-100' : 'opacity-0'}`}>
        <div className="absolute top-2 left-2 w-1 h-1 bg-white rounded-full animate-pulse" />
        <div className="absolute top-4 left-4 w-0.5 h-0.5 bg-white/70 rounded-full" />
        <div className="absolute bottom-2 left-3 w-0.5 h-0.5 bg-white/50 rounded-full" />
      </div>

      {/* Sliding knob */}
      <div
        className={`
          absolute top-1 w-6 h-6 rounded-full
          flex items-center justify-center
          transition-all duration-500 ease-[cubic-bezier(0.68,-0.55,0.265,1.55)]
          shadow-lg z-10
          ${isDark
            ? 'translate-x-[32px] bg-slate-900'
            : 'translate-x-1 bg-white'
          }
        `}
      >
        {/* Sun icon */}
        <Sun
          className={`
            absolute w-3.5 h-3.5 text-amber-500
            transition-all duration-500 ease-in-out
            ${isDark ? 'opacity-0 rotate-90 scale-0' : 'opacity-100 rotate-0 scale-100'}
          `}
        />
        {/* Moon icon */}
        <Moon
          className={`
            absolute w-3.5 h-3.5 text-indigo-300
            transition-all duration-500 ease-in-out
            ${isDark ? 'opacity-100 rotate-0 scale-100' : 'opacity-0 -rotate-90 scale-0'}
          `}
        />
      </div>
    </button>
  )
}
