import type { MetadataRoute } from 'next'

import { getSiteUrl } from '@/lib/site-url'

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
        allow: ['/restaurants', '/restaurants/'],
        disallow: '/',
      },
    ],
    sitemap: new URL('/sitemap.xml', siteUrl).toString(),
  }
}
