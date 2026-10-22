/** @type {import('next').NextConfig} */
// Static export, served by Cloudflare Pages (project lifeplanner-site).
// There is no server: the app-link files live in public/.well-known.
const nextConfig = {
  output: 'export',
  images: {
    unoptimized: true,
  },
  devIndicators: false,
}

module.exports = nextConfig
