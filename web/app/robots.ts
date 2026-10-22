import { MetadataRoute } from 'next';

export const dynamic = 'force-static';

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: '*',
        allow: '/',
        disallow: ['/admin/', '/auth/', '/_next/'],
      },
    ],
    sitemap: 'https://lifeplanner.tribe.az/sitemap.xml',
  };
}
