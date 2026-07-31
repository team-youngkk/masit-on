'use client'

import { authenticatedMemberFetch } from '@/lib/member/auth'

export type PersonalRestaurantSummary = {
  id: string
  name: string
  district: string
  category: string
}

export type PersonalRestaurantPage = {
  number: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export type FavoriteState = {
  restaurantId: string
  favorited: boolean
}

export type FavoriteRestaurantItem = {
  restaurant: PersonalRestaurantSummary
  favoritedAt: string
}

export type FavoriteRestaurantsResponse = {
  items: FavoriteRestaurantItem[]
  page: PersonalRestaurantPage
}

export type RecentRestaurantItem = {
  restaurant: PersonalRestaurantSummary
  lastViewedAt: string
}

export type RecentRestaurantsResponse = {
  items: RecentRestaurantItem[]
  page: PersonalRestaurantPage
}

export type RecentRestaurantState = {
  restaurantId: string
  recorded: boolean
}

async function jsonResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    // Callers distinguish 401 and read the contract error body from this response.
    throw response
  }
  return (await response.json()) as T
}

function restaurantPath(path: string, restaurantId: string): string {
  return `${path}/${encodeURIComponent(restaurantId)}`
}

export async function getFavoriteState(
  restaurantId: string,
): Promise<boolean> {
  const response = await authenticatedMemberFetch(
    restaurantPath('/api/me/favorites', restaurantId),
    { cache: 'no-store' },
  )
  return (await jsonResponse<FavoriteState>(response)).favorited
}

export async function setFavoriteState(
  restaurantId: string,
  favorited: boolean,
): Promise<boolean> {
  const response = await authenticatedMemberFetch(
    restaurantPath('/api/me/favorites', restaurantId),
    { method: favorited ? 'PUT' : 'DELETE' },
  )
  return (await jsonResponse<FavoriteState>(response)).favorited
}

export async function getFavorites(
  page: number,
  size: number,
): Promise<FavoriteRestaurantsResponse> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  const response = await authenticatedMemberFetch(
    `/api/me/favorites?${params.toString()}`,
    { cache: 'no-store' },
  )
  return jsonResponse<FavoriteRestaurantsResponse>(response)
}

export async function getRecentRestaurants(
  page: number,
  size: number,
): Promise<RecentRestaurantsResponse> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  const response = await authenticatedMemberFetch(
    `/api/me/recent-restaurants?${params.toString()}`,
    { cache: 'no-store' },
  )
  return jsonResponse<RecentRestaurantsResponse>(response)
}

export async function removeRecentRestaurant(
  restaurantId: string,
): Promise<RecentRestaurantState> {
  const response = await authenticatedMemberFetch(
    restaurantPath('/api/me/recent-restaurants', restaurantId),
    { method: 'DELETE' },
  )
  return jsonResponse<RecentRestaurantState>(response)
}
