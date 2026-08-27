import type { MetadataRoute } from 'next'

import { fetchRestaurants } from '@/lib/restaurants-api'
import { getSiteUrl } from '@/lib/site-url'

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

  for (const restaurantId of restaurantIds) {
    entries.push({
      url: new URL(`/restaurants/${encodeURIComponent(restaurantId)}`, siteUrl).toString(),
    })
  }

  return entries
}

async function getPublicRestaurantIds(): Promise<string[]> {
  const restaurantIds: string[] = []
  let page = 1

  while (true) {
    const params = new URLSearchParams({ page: String(page), size: SITEMAP_PAGE_SIZE })
    const result = await fetchRestaurants(params, {
      next: { revalidate: SITEMAP_REVALIDATE_SECONDS },
    })
    if (!result.ok) {
      throw new Error('공개 맛집 sitemap 목록을 불러오지 못했습니다.')
    }

    restaurantIds.push(
      ...result.data.items
        .map((restaurant) => restaurant.id)
        .filter((restaurantId) => restaurantId.trim().length > 0),
    )
    if (!result.data.page.hasNext || page >= result.data.page.totalPages) {
      return restaurantIds
    }
    page += 1
  }
}
