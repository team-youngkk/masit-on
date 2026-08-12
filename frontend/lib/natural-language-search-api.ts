'use client'

import { parseRetryAfterHeader } from './map/retry-after.ts'

export type NaturalLanguageSearchFilters = { query: string | null; district: string | null; category: string | null; creatorId: string | null; tags: string[] }
export type NaturalLanguageCondition = NaturalLanguageSearchFilters
export type NaturalLanguageRestaurant = { id: string; name: string; district: string; category: string; visitedBy: Array<{ id: string; channelName: string }>; remainingVisitedByCount: number }
export type NaturalLanguageResult = { interpretation: { status: 'APPLIED' | 'PARTIAL' | 'FAILED'; appliedConditions: NaturalLanguageCondition; ignoredConditions: Array<{ type: string; text: string; reason: string }>; conflicts: Array<{ field: string; resolution: string }>; parserVersion: string }; results: { items: NaturalLanguageRestaurant[]; page: { number: number; size: number; totalElements: number; totalPages: number; hasNext: boolean } } }
export type NaturalLanguageSearchOutcome = { kind: 'success'; result: NaturalLanguageResult } | { kind: 'invalid'; message: string; traceId?: string } | { kind: 'rateLimited'; message: string; traceId?: string; retryAvailableAt: number | null } | { kind: 'unavailable'; message: string; traceId?: string } | { kind: 'error'; message: string; traceId?: string; retryAllowed: boolean }

export function naturalLanguageFiltersFromFormData(data: FormData, tags: string[] = []): NaturalLanguageSearchFilters {
  const value = (name: string) => {
    const entry = data.get(name)
    return typeof entry === 'string' && entry.trim() ? entry.trim() : null
  }
  return { query: value('query'), district: value('district'), category: value('category'), creatorId: value('creatorId'), tags }
}

const FALLBACK_ERROR = '자연어 검색을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
const FALLBACK_INVALID = '입력한 문장 또는 필터를 확인해 주세요.'
const FALLBACK_RATE_LIMIT = '요청이 많습니다. 잠시 후 다시 시도해 주세요.'
const FALLBACK_UNAVAILABLE = '자연어 검색을 지금 사용할 수 없습니다. 기존 필터 검색을 이용해 주세요.'
const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
const strings = (value: unknown): string[] | null => Array.isArray(value) && value.every((item) => typeof item === 'string') ? value : null

function condition(value: unknown): NaturalLanguageCondition | null {
  if (!isRecord(value) || strings(value.tags) === null) return null
  for (const field of ['query', 'district', 'category', 'creatorId'] as const) if (value[field] !== null && typeof value[field] !== 'string') return null
  return { query: value.query as string | null, district: value.district as string | null, category: value.category as string | null, creatorId: value.creatorId as string | null, tags: strings(value.tags)! }
}

export function decodeNaturalLanguageResult(value: unknown): NaturalLanguageResult | null {
  if (!isRecord(value) || !isRecord(value.interpretation) || !isRecord(value.results)) return null
  const interpretation = value.interpretation; const results = value.results; const appliedConditions = condition(interpretation.appliedConditions)
  if (!['APPLIED', 'PARTIAL', 'FAILED'].includes(String(interpretation.status)) || !appliedConditions || !Array.isArray(interpretation.ignoredConditions) || !Array.isArray(interpretation.conflicts) || typeof interpretation.parserVersion !== 'string' || !Array.isArray(results.items) || !isRecord(results.page)) return null
  const page = results.page
  if (typeof page.number !== 'number' || typeof page.size !== 'number' || typeof page.totalElements !== 'number' || typeof page.totalPages !== 'number' || typeof page.hasNext !== 'boolean') return null
  const ignoredConditions = interpretation.ignoredConditions.flatMap((item) => isRecord(item) && typeof item.type === 'string' && typeof item.text === 'string' && typeof item.reason === 'string' ? [{ type: item.type, text: item.text, reason: item.reason }] : [])
  const conflicts = interpretation.conflicts.flatMap((item) => isRecord(item) && typeof item.field === 'string' && typeof item.resolution === 'string' ? [{ field: item.field, resolution: item.resolution }] : [])
  if (ignoredConditions.length !== interpretation.ignoredConditions.length || conflicts.length !== interpretation.conflicts.length) return null
  const items: NaturalLanguageRestaurant[] = []
  for (const item of results.items) { if (!isRecord(item) || typeof item.id !== 'string' || typeof item.name !== 'string' || typeof item.district !== 'string' || typeof item.category !== 'string' || typeof item.remainingVisitedByCount !== 'number' || !Array.isArray(item.visitedBy)) return null; const visitedBy = item.visitedBy.flatMap((creator) => isRecord(creator) && typeof creator.id === 'string' && typeof creator.channelName === 'string' ? [{ id: creator.id, channelName: creator.channelName }] : []); if (visitedBy.length !== item.visitedBy.length) return null; items.push({ id: item.id, name: item.name, district: item.district, category: item.category, visitedBy, remainingVisitedByCount: item.remainingVisitedByCount }) }
  return { interpretation: { status: interpretation.status as NaturalLanguageResult['interpretation']['status'], appliedConditions, ignoredConditions, conflicts, parserVersion: interpretation.parserVersion }, results: { items, page: { number: page.number, size: page.size, totalElements: page.totalElements, totalPages: page.totalPages, hasNext: page.hasNext } } }
}

export function isNaturalLanguageRetryAllowed(outcome: NaturalLanguageSearchOutcome): boolean { return outcome.kind === 'rateLimited' || outcome.kind === 'unavailable' || (outcome.kind === 'error' && outcome.retryAllowed) }

export async function searchRestaurantsByNaturalLanguage(sentence: string, filters: NaturalLanguageSearchFilters, page = 1, signal?: AbortSignal): Promise<NaturalLanguageSearchOutcome> {
  let response: Response
  try { response = await fetch('/api/restaurants/natural-language-search', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sentence, filters, page, size: 20 }), cache: 'no-store', signal }) } catch (error) { if (error instanceof DOMException && error.name === 'AbortError') throw error; return { kind: 'error', message: FALLBACK_ERROR, retryAllowed: true } }
  let body: unknown = null; try { body = await response.json() } catch { body = null }
  if (response.ok) { const result = decodeNaturalLanguageResult(body); return result ? { kind: 'success', result } : { kind: 'error', message: FALLBACK_ERROR, retryAllowed: false } }
  const error = isRecord(body) ? body : {}; const message = typeof error.message === 'string' ? error.message : undefined; const traceId = typeof error.traceId === 'string' ? error.traceId : undefined
  if (response.status >= 400 && response.status < 500 && response.status !== 429) return { kind: 'invalid', message: message ?? FALLBACK_INVALID, traceId }
  if (response.status === 429) return { kind: 'rateLimited', message: message ?? FALLBACK_RATE_LIMIT, traceId, retryAvailableAt: parseRetryAfterHeader(response.headers.get('Retry-After'), Date.now()) }
  if (response.status === 503) return { kind: 'unavailable', message: message ?? FALLBACK_UNAVAILABLE, traceId }
  return { kind: 'error', message: message ?? FALLBACK_ERROR, traceId, retryAllowed: response.status >= 500 }
}
