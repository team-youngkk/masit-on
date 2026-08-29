import type { MetadataRoute } from 'next'

import { getSiteUrl } from '../lib/site-url.ts'

export const dynamic = 'force-dynamic'

export default function robots(): MetadataRoute.Robots {
  const siteUrl = getSiteUrl()
  if (!siteUrl) {
    return {
      rules: {
        userAgent: '*',
        disallow: '/',
      },
    }
  }

  return {
    rules: [
      {
        userAgent: '*',
        allow: ['/restaurants', '/restaurants/', '/sitemap.xml', '/_next/'],
        disallow: '/',
      },
    ],
    sitemap: new URL('/sitemap.xml', siteUrl).toString(),
  }
}
