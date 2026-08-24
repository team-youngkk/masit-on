'use client'

import { getFavorites } from '../member/personal-restaurants.ts'

import type { CourseCandidate } from './course-selection'

/* 개인 찜 API의 허용 크기 중 최댓값을 사용하고, page.hasNext로 추가 목록을 이어 받는다. */
export const COURSE_FAVORITES_PAGE_SIZE = 50

export type CourseFavoritesPage = {
  number: number
  size: number
  hasNext: boolean
}

export type CourseFavoritesResult =
  | { ok: true; items: CourseCandidate[]; page: CourseFavoritesPage }
  | { ok: false; status?: number; message: string; traceId?: string }

const FALLBACK_ERROR_MESSAGE =
  '찜 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

export function normalizeCourseFavoriteItems(rawItems: unknown[]): CourseCandidate[] {
  const items: CourseCandidate[] = []
  for (const rawItem of rawItems) {
    if (typeof rawItem !== 'object' || rawItem === null) continue
    const favorite = rawItem as { restaurant?: unknown }
    if (typeof favorite.restaurant !== 'object' || favorite.restaurant === null) continue

    const restaurant = favorite.restaurant as Record<string, unknown>
    if (
      typeof restaurant.id === 'string' && restaurant.id.length > 0 &&
      typeof restaurant.name === 'string' && restaurant.name.length > 0 &&
      typeof restaurant.district === 'string' &&
      typeof restaurant.category === 'string'
    ) {
      items.push({
        id: restaurant.id,
        name: restaurant.name,
        district: restaurant.district,
        category: restaurant.category,
      })
    }
  }
  return items
}

export async function getCourseFavorites(
  page = 1,
  signal?: AbortSignal,
): Promise<CourseFavoritesResult> {
  try {
    const response = await getFavorites(page, COURSE_FAVORITES_PAGE_SIZE, signal)
    return {
      ok: true,
      items: normalizeCourseFavoriteItems(response.items),
      page: normalizeCourseFavoritesPage(response.page, page),
    }
  } catch (error) {
    if (isAbortError(error)) throw error

    if (error instanceof Response) {
      const body = await readErrorBody(error)
      return {
        ok: false,
        status: error.status,
        message: body?.message ?? (error.status === 401
          ? '로그인 후 찜 목록에서 맛집을 선택할 수 있습니다.'
          : FALLBACK_ERROR_MESSAGE),
        traceId: body?.traceId,
      }
    }

    return { ok: false, message: FALLBACK_ERROR_MESSAGE }
  }
}

function normalizeCourseFavoritesPage(
  rawPage: unknown,
  requestedPage: number,
): CourseFavoritesPage {
  if (typeof rawPage !== 'object' || rawPage === null) {
    return { number: requestedPage, size: COURSE_FAVORITES_PAGE_SIZE, hasNext: false }
  }

  const page = rawPage as Record<string, unknown>
  return {
    number:
      typeof page.number === 'number' && Number.isInteger(page.number) && page.number > 0
        ? page.number
        : requestedPage,
    size:
      typeof page.size === 'number' && Number.isInteger(page.size) && page.size > 0
        ? page.size
        : COURSE_FAVORITES_PAGE_SIZE,
    hasNext: page.hasNext === true,
  }
}

async function readErrorBody(response: Response): Promise<{ message?: string; traceId?: string } | null> {
  try {
    return (await response.json()) as { message?: string; traceId?: string }
  } catch {
    return null
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}
