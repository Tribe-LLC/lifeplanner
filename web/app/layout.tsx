import type { Metadata } from 'next'
import localFont from 'next/font/local'
import Script from 'next/script'
import { ThemeProvider } from '@/components/landing/theme-provider'
import { FirebaseAnalytics } from '@/components/firebase-analytics'
import './globals.css'

// Satoshi is the app's own typeface (copied from composeResources/font).
const satoshi = localFont({
  src: [
    { path: './fonts/Satoshi-Regular.ttf', weight: '400', style: 'normal' },
    { path: './fonts/Satoshi-Medium.ttf', weight: '500', style: 'normal' },
    { path: './fonts/Satoshi-Bold.ttf', weight: '700', style: 'normal' },
  ],
  variable: '--font-sans',
  display: 'swap',
})

export const metadata: Metadata = {
  title: {
    default: 'Life Planner: your whole life, one calm planner',
    template: '%s | Life Planner',
  },
  description:
    'Habits, money, travel, meals, study and more in one calm planner, with a coach that sees your whole week. Free on iPhone and Android.',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${satoshi.variable} font-sans antialiased`}>
        <Script
          id="gtm-script"
          strategy="beforeInteractive"
          dangerouslySetInnerHTML={{
            __html: `(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':
new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],
j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src=
'https://www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);
})(window,document,'script','dataLayer','GTM-N92VW4XL');`,
          }}
        />
        <noscript>
          <iframe
            src="https://www.googletagmanager.com/ns.html?id=GTM-N92VW4XL"
            height="0"
            width="0"
            style={{ display: 'none', visibility: 'hidden' }}
          />
        </noscript>
        {/* Microsoft Clarity. Public pages only: never admin, auth or chat. The id must not be "clarity", ids become window globals. */}
        <Script
          id="ms-clarity-tag"
          strategy="afterInteractive"
          dangerouslySetInnerHTML={{
            __html: String.raw`(function(w,d,H,I,S,C){if(!H.test(location.hostname)||/^\/(admin|auth|chat|api)(\/|$)/.test(location.pathname))return;(function(c,l,a,r,i,t,y){c[a]=c[a]||function(){(c[a].q=c[a].q||[]).push(arguments)};t=l.createElement(r);t.async=1;t.src="https://www.clarity.ms/tag/"+i;y=l.getElementsByTagName(r)[0];y.parentNode.insertBefore(t,y)})(w,d,"clarity","script",I);function E(n){w.clarity("event",n)}d.addEventListener("click",function(e){var t=e.target;if(!t.closest)return;for(var k in C)if(t.closest(k))E(C[k]);var a=t.closest("a[href]");if(!a)return;var h=a.href,n=/apps\.apple\.com/.test(h)?"appstore_click":/play\.google\.com/.test(h)?"play_click":/wa\.me|whatsapp\.com/.test(h)?"whatsapp_click":/^tel:/.test(h)?"phone_click":/instagram\.com/.test(h)?"instagram_click":/google\.[a-z.]+\/maps|maps\.app\.goo\.gl|goo\.gl\/maps/.test(h)?"map_click":"";if(n)E(n)},true);d.addEventListener("submit",function(e){var l=e.target.getAttribute("data-lead");E(l?"lead_form_"+l:"form_submit")},true);function O(){if(!w.IntersectionObserver)return;Object.keys(S).forEach(function(id){var x=d.getElementById(id);if(!x)return;var o=new IntersectionObserver(function(es){if(es[0].isIntersecting){E(S[id]);o.disconnect()}},{rootMargin:"-30% 0px"});o.observe(x)})}d.readyState=="loading"?d.addEventListener("DOMContentLoaded",O):O()})(window,document,/^lifeplanner\.tribe\.az$/,"z99sfc3c5l",{},{});`,
          }}
        />
        {/* Meta Pixel */}
        <Script
          id="meta-pixel"
          strategy="beforeInteractive"
          dangerouslySetInnerHTML={{
            __html: `!function(f,b,e,v,n,t,s)
{if(f.fbq)return;n=f.fbq=function(){n.callMethod?
n.callMethod.apply(n,arguments):n.queue.push(arguments)};
if(!f._fbq)f._fbq=n;n.push=n;n.loaded=!0;n.version='2.0';
n.queue=[];t=b.createElement(e);t.async=!0;
t.src=v;s=b.getElementsByTagName(e)[0];
s.parentNode.insertBefore(t,s)}(window, document,'script',
'https://connect.facebook.net/en_US/fbevents.js');
fbq('init', '1841222806541833');
fbq('track', 'PageView');`,
          }}
        />
        <noscript>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            height="1"
            width="1"
            style={{ display: 'none' }}
            src="https://www.facebook.com/tr?id=1841222806541833&ev=PageView&noscript=1"
            alt=""
          />
        </noscript>
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
        >
          {children}
        </ThemeProvider>
        <FirebaseAnalytics />
      </body>
    </html>
  )
}
