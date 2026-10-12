import type { CourseSearchFilters } from './course-search-api.ts'

export type CourseScreenSearchFilters = {
  query: string
  regionCode: string
  category: string
}

export function normalizeCourseScreenSearchFilters(
  filters: CourseSearchFilters,
): CourseScreenSearchFilters {
  return {
    query: filters.query?.trim() ?? '',
    regionCode: filters.regionCode?.trim() ?? '',
    category: filters.category?.trim() ?? '',
  }
}

export function courseScreenSearchFiltersKey(filters: CourseScreenSearchFilters): string {
  return [filters.query, filters.regionCode, filters.category].join('\u001f')
}

export function resetCourseSearchState(filters: CourseScreenSearchFilters) {
  return {
    status: 'idle' as const,
    items: [],
    page: 1,
    hasNext: false,
    loadingMore: false,
    filters,
  }
}

export function isCurrentCourseSearchRequest(activeRequestId: number, requestId: number): boolean {
  return activeRequestId === requestId
}
