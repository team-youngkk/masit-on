import type { Metadata } from 'next'

import type { RawSearchParams } from './restaurants-api.ts'
import { toPublicSiteUrl } from './site-url.ts'

const RESTAURANTS_TITLE = '유튜버가 방문한 맛집 탐색 | 맛잇온'
const RESTAURANTS_DESCRIPTION =
  '유튜버가 방문한 서울 맛집을 지역, 음식 종류, 유튜버로 탐색하세요.'

type RestaurantMetadataState = {
  requestSucceeded: boolean
  hasItems: boolean
}

type RestaurantDetailMetadataData = {
  name: string
  category: string
}

export function buildRestaurantsMetadata(
  rawParams: RawSearchParams,
  state: RestaurantMetadataState,
): Metadata {
  const canonical = toPublicSiteUrl('/restaurants')
  const canonicalMetadata = canonical ? { alternates: { canonical } } : {}

  if (Object.keys(rawParams).length > 0) {
    return {
      ...canonicalMetadata,
      robots: { index: false, follow: true },
    }
  }

  if (!canonical || !state.requestSucceeded || !state.hasItems) {
    return {
      ...canonicalMetadata,
      robots: { index: false, follow: false },
    }
  }

  return {
    ...canonicalMetadata,
    title: RESTAURANTS_TITLE,
    description: RESTAURANTS_DESCRIPTION,
    robots: { index: true, follow: true },
  }
}

export function buildRestaurantDetailMetadata(
  rawParams: RawSearchParams,
  canonical: string | null,
  restaurant?: RestaurantDetailMetadataData,
): Metadata {
  const canonicalMetadata = canonical
    ? { alternates: { canonical } }
    : {}
  const hasQueryParams = Object.keys(rawParams).length > 0

  if (!canonical || !restaurant) {
    return {
      ...canonicalMetadata,
      robots: { index: false, follow: false },
    }
  }

  return {
    ...canonicalMetadata,
    title: `${restaurant.name} | ${restaurant.category} 맛집 | 맛잇온`,
    description: `유튜버가 방문한 ${restaurant.category} 맛집 ${restaurant.name}의 정보를 확인하세요.`,
    robots: { index: !hasQueryParams, follow: true },
  }
}
