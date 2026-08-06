'use client'

import { AdminApiError, adminJson, messageFor } from './api'
import type { CurationStatus } from './curations-coordination'

export type { CurationStatus }

export type AdminCurationRestaurant = {
  restaurantId: string
  position: number
  name: string | null
  availability: string
  warning: string | null
}

export type AdminCuration = {
  curationId: string
  title: string
  description: string
  status: CurationStatus
  mainPosition: number | null
  restaurantCount: number
  hasHiddenRestaurants: boolean
  publishedAt: string | null
  updatedAt: string
  items: AdminCurationRestaurant[]
}

export type AdminCurationPage = {
  items: AdminCuration[]
  page: { number: number; size: number; totalElements: number; totalPages: number; hasNext: boolean }
}

type RawCurationSummary = Omit<AdminCuration, 'items'>
type RawCurationDetail = Omit<AdminCuration, 'restaurantCount' | 'hasHiddenRestaurants'> & {
  items: AdminCurationRestaurant[]
}

function normalizeSummary(raw: RawCurationSummary): AdminCuration {
  return {
    ...raw,
    items: [],
  }
}

function normalizeDetail(raw: RawCurationDetail): AdminCuration {
  return {
    ...raw,
    restaurantCount: raw.items.length,
    hasHiddenRestaurants: raw.items.some((item) => Boolean(item.warning)
      || Boolean(item.availability && item.availability !== 'PUBLIC')),
  }
}

export async function getAdminCurations(page: number, status: CurationStatus | ''): Promise<AdminCurationPage> {
  const query = new URLSearchParams({ page: String(page), size: '20' })
  if (status) query.set('status', status)
  const response = await adminJson<{ items: RawCurationSummary[]; page: AdminCurationPage['page'] }>(`/api/admin/curations?${query}`, { cache: 'no-store' })
  return { ...response, items: response.items.map(normalizeSummary) }
}

export async function getAdminCuration(id: string): Promise<AdminCuration> {
  return normalizeDetail(await adminJson<RawCurationDetail>(`/api/admin/curations/${encodeURIComponent(id)}`, { cache: 'no-store' }))
}

export async function createAdminCuration(title: string, description: string, idempotencyKey: string): Promise<AdminCuration> {
  return normalizeDetail(await adminJson<RawCurationDetail>('/api/admin/curations', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ title: title.trim(), description: description.trim() }),
  }))
}

export async function updateAdminCuration(id: string, title: string, description: string): Promise<AdminCuration> {
  return normalizeDetail(await adminJson<RawCurationDetail>(`/api/admin/curations/${encodeURIComponent(id)}`, {
    method: 'PATCH', body: JSON.stringify({ title: title.trim(), description: description.trim() }),
  }))
}

export async function replaceCurationRestaurants(id: string, restaurantIds: string[]): Promise<AdminCuration> {
  return normalizeDetail(await adminJson<RawCurationDetail>(`/api/admin/curations/${encodeURIComponent(id)}/restaurants`, {
    method: 'PUT', body: JSON.stringify({ restaurantIds }),
  }))
}

export async function setCurationPublication(id: string, status: CurationStatus): Promise<AdminCuration> {
  return normalizeDetail(await adminJson<RawCurationDetail>(`/api/admin/curations/${encodeURIComponent(id)}/publication`, {
    method: 'PUT', body: JSON.stringify({ status }),
  }))
}

export async function replaceMainCurationOrder(curationIds: string[]): Promise<unknown> {
  return adminJson('/api/admin/curations/main-order', { method: 'PUT', body: JSON.stringify({ curationIds }) })
}

const CONFLICT_MESSAGES: Record<string, string> = {
  DUPLICATE_CURATION_RESTAURANT: '같은 맛집을 한 큐레이션에 중복 배치할 수 없습니다.',
  CURATION_RESTAURANT_LIMIT_EXCEEDED: '맛집은 큐레이션당 최대 20개까지 구성할 수 있습니다.',
  PUBLISHED_CURATION_LIMIT_EXCEEDED: '게시 큐레이션은 최대 5개입니다. 기존 게시를 중단한 뒤 다시 시도해 주세요.',
  INVALID_MAIN_CURATION_ORDER: '게시 중인 큐레이션 전체와 현재 순서 요청이 일치하지 않습니다. 목록을 새로고침한 뒤 다시 시도해 주세요.',
}

export function curationMessageFor(reason: unknown): string {
  if (!(reason instanceof AdminApiError) || !reason.code || !CONFLICT_MESSAGES[reason.code]) return messageFor(reason)
  const message = CONFLICT_MESSAGES[reason.code]
  return reason.traceId ? `${message} (문의 ID: ${reason.traceId})` : message
}
