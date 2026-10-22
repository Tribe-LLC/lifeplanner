import type { Config } from 'tailwindcss'

const config: Config = {
  darkMode: 'class',
  content: [
    './pages/**/*.{js,ts,jsx,tsx,mdx}',
    './components/**/*.{js,ts,jsx,tsx,mdx}',
    './app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['var(--font-sans)', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
      // The app's v4 palette (ui/v4/theme/V4Theme.kt).
      colors: {
        lp: {
          primary: '#3B5BE5',
          'primary-dark': '#6D86F5',
          violet: '#2C42B0',
          coral: '#EA580C',
          bg: '#F6F5F1',
          surface: '#FFFFFF',
          tint: '#E6EBFF',
          ink: '#15171C',
          muted: '#5A5D66',
          line: '#E4E2DB',
          'bg-dark': '#111317',
          'surface-dark': '#1A1D22',
          'tint-dark': '#1F2744',
          'ink-dark': '#F2F1EC',
          'muted-dark': '#9EA1A9',
          'line-dark': '#2B2F36',
        },
      },
    },
  },
  plugins: [],
}
export default config
