'use client'

import { authenticatedMemberFetch } from './auth'

export type CollectionSummary = {
  collectionId: string
  name: string
  restaurantCount: number
  updatedAt: string
}

export type CollectionRestaurant = {
  restaurantId: string
  name: string
  roadAddress: string
  addedAt: string
}

export type CollectionPage = {
  number: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export type CollectionDetail = CollectionSummary & {
  items: CollectionRestaurant[]
  page: CollectionPage
}

type ApiErrorBody = {
  code?: string
  message?: string
  traceId?: string
}

export class CollectionApiError extends Error {
  constructor(
    readonly status: number,
    readonly code?: string,
    message = '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    readonly traceId?: string,
  ) {
    super(message)
  }
}

async function requireResponse(response: Response): Promise<Response> {
  if (response.ok) return response

  let body: ApiErrorBody | undefined
  try {
    body = (await response.json()) as ApiErrorBody
  } catch {
    // 상태 코드는 유지하므로 JSON이 아닌 게이트웨이 오류도 처리할 수 있다.
  }
  throw new CollectionApiError(
    response.status,
    body?.code,
    body?.message,
    body?.traceId,
  )
}

function collectionPath(collectionId: string): string {
  return `/api/me/collections/${encodeURIComponent(collectionId)}`
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

export async function getCollections(): Promise<CollectionSummary[]> {
  const response = await requireResponse(
    await authenticatedMemberFetch('/api/me/collections', { cache: 'no-store' }),
  )
  return ((await response.json()) as { items: CollectionSummary[] }).items
}

export async function createCollection(
  name: string,
  idempotencyKey: string,
): Promise<CollectionSummary> {
  const response = await requireResponse(
    await authenticatedMemberFetch('/api/me/collections', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify({ name }),
    }),
  )
  return (await response.json()) as CollectionSummary
}

export async function getCollection(
  collectionId: string,
  page: number,
  size: number,
): Promise<CollectionDetail> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  const response = await requireResponse(
    await authenticatedMemberFetch(
      `${collectionPath(collectionId)}?${params.toString()}`,
      { cache: 'no-store' },
    ),
  )
  return (await response.json()) as CollectionDetail
}

export async function renameCollection(
  collectionId: string,
  name: string,
): Promise<CollectionSummary> {
  const response = await requireResponse(
    await authenticatedMemberFetch(collectionPath(collectionId), {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name }),
    }),
  )
  return (await response.json()) as CollectionSummary
}

export async function deleteCollection(collectionId: string): Promise<void> {
  await requireResponse(
    await authenticatedMemberFetch(collectionPath(collectionId), {
      method: 'DELETE',
    }),
  )
}

export async function removeRestaurantFromCollection(
  collectionId: string,
  restaurantId: string,
): Promise<void> {
  await requireResponse(
    await authenticatedMemberFetch(
      `${collectionPath(collectionId)}/restaurants/${encodeURIComponent(restaurantId)}`,
      { method: 'DELETE' },
    ),
  )
}

export async function addRestaurantToCollection(
  collectionId: string,
  restaurantId: string,
): Promise<{ collectionId: string; restaurantId: string; addedAt: string }> {
  const response = await requireResponse(
    await authenticatedMemberFetch(
      `${collectionPath(collectionId)}/restaurants/${encodeURIComponent(restaurantId)}`,
      { method: 'PUT' },
    ),
  )
  return (await response.json()) as {
    collectionId: string
    restaurantId: string
    addedAt: string
  }
}
