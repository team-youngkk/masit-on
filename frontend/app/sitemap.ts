import type { MetadataRoute } from 'next'

import { fetchRestaurants } from '../lib/restaurants-api.ts'
import { getSiteUrl } from '../lib/site-url.ts'

const SITEMAP_PAGE_SIZE = '50'
const SITEMAP_REVALIDATE_SECONDS = 300

export const dynamic = 'force-dynamic'

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const siteUrl = getSiteUrl()
  if (!siteUrl) {
    return []
  }

  const entries: MetadataRoute.Sitemap = [
    { url: new URL('/restaurants', siteUrl).toString() },
  ]
  const restaurantIds = await getPublicRestaurantIds()

  if (restaurantIds == null) {
    return entries
  }

  for (const restaurantId of restaurantIds) {
    entries.push({
      url: new URL(`/restaurants/${encodeURIComponent(restaurantId)}`, siteUrl).toString(),
    })
  }

  return entries
}

async function getPublicRestaurantIds(): Promise<string[] | null> {
  const restaurantIds = new Set<string>()
  let page = 1

  try {
    while (true) {
      const params = new URLSearchParams({ page: String(page), size: SITEMAP_PAGE_SIZE })
      const result = await fetchRestaurants(params, {
        next: { revalidate: SITEMAP_REVALIDATE_SECONDS },
      })
      if (!result.ok) {
        return null
      }

      for (const restaurant of result.data.items) {
        if (restaurant.id.trim().length > 0) {
          restaurantIds.add(restaurant.id)
        }
      }
      if (!result.data.page.hasNext || page >= result.data.page.totalPages) {
        return [...restaurantIds]
      }
      page += 1
    }
  } catch {
    return null
  }
}
