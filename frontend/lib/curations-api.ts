/*
 * 공개 큐레이션 조회 연동 전용 타입·헬퍼.
 * 계약: docs/05-specs/api/curation/curation-api.md 2절
 *       docs/05-specs/api/common/error-contract.md
 */

const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'
const FALLBACK_ERROR_MESSAGE =
  '큐레이션을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

export type PublicCurationRestaurant = {
  restaurantId: string
  name: string
  roadAddress: string
}

export type PublicCuration = {
  curationId: string
  title: string
  description: string
  items: PublicCurationRestaurant[]
  publishedAt: string
  updatedAt: string
}

export type PublicCurationsResponse = {
  items: PublicCuration[]
}

type ApiErrorBody = {
  code?: string
  message?: string
  traceId?: string
}

export type FetchPublicCurationsResult =
  | { ok: true; data: PublicCurationsResponse }
  | { ok: false; message: string; traceId?: string }

export type FetchPublicCurationResult =
  | { ok: true; data: PublicCuration }
  | { ok: false; kind: 'not-found' }
  | { ok: false; kind: 'error'; message: string; traceId?: string }

export async function fetchPublicCurations(): Promise<FetchPublicCurationsResult> {
  let response: Response

  try {
    response = await fetch(`${API_BASE_URL}/api/curations`, {
      cache: 'no-store',
    })
  } catch {
    return { ok: false, message: FALLBACK_ERROR_MESSAGE }
  }

  if (!response.ok) {
    const body = await readErrorBody(response)
    return {
      ok: false,
      message: body?.message ?? FALLBACK_ERROR_MESSAGE,
      traceId: body?.traceId,
    }
  }

  try {
    const data = decodePublicCurationsResponse(await response.json())
    return data == null
      ? { ok: false, message: FALLBACK_ERROR_MESSAGE }
      : { ok: true, data }
  } catch {
    return { ok: false, message: FALLBACK_ERROR_MESSAGE }
  }
}

export async function fetchPublicCuration(
  curationId: string,
): Promise<FetchPublicCurationResult> {
  let response: Response

  try {
    response = await fetch(
      `${API_BASE_URL}/api/curations/${encodeURIComponent(curationId)}`,
      { cache: 'no-store' },
    )
  } catch {
    return { ok: false, kind: 'error', message: FALLBACK_ERROR_MESSAGE }
  }

  if (!response.ok) {
    const body = await readErrorBody(response)
    if (response.status === 404 && body?.code === 'CURATION_NOT_FOUND') {
      return { ok: false, kind: 'not-found' }
    }
    // 공개 화면은 내부 식별자 형식을 전제하지 않으므로 영구적인 형식 오류도 미존재와 같이 닫는다.
    if (response.status === 400 && body?.code === 'INVALID_IDENTIFIER') {
      return { ok: false, kind: 'not-found' }
    }
    return {
      ok: false,
      kind: 'error',
      message: body?.message ?? FALLBACK_ERROR_MESSAGE,
      traceId: body?.traceId,
    }
  }

  try {
    const data = decodePublicCuration(await response.json())
    return data == null
      ? { ok: false, kind: 'error', message: FALLBACK_ERROR_MESSAGE }
      : { ok: true, data }
  } catch {
    return {
      ok: false,
      kind: 'error',
      message: FALLBACK_ERROR_MESSAGE,
    }
  }
}

function decodePublicCurationsResponse(value: unknown): PublicCurationsResponse | null {
  if (!isRecord(value) || !Array.isArray(value.items)) {
    return null
  }

  const items = value.items.map(decodePublicCuration)
  return items.every((item): item is PublicCuration => item != null)
    ? { items }
    : null
}

function decodePublicCuration(value: unknown): PublicCuration | null {
  if (
    !isRecord(value) ||
    typeof value.curationId !== 'string' ||
    typeof value.title !== 'string' ||
    typeof value.description !== 'string' ||
    !Array.isArray(value.items) ||
    typeof value.publishedAt !== 'string' ||
    typeof value.updatedAt !== 'string'
  ) {
    return null
  }

  const items = value.items.map(decodePublicCurationRestaurant)
  if (!items.every((item): item is PublicCurationRestaurant => item != null)) {
    return null
  }

  return {
    curationId: value.curationId,
    title: value.title,
    description: value.description,
    items,
    publishedAt: value.publishedAt,
    updatedAt: value.updatedAt,
  }
}

function decodePublicCurationRestaurant(
  value: unknown,
): PublicCurationRestaurant | null {
  if (
    !isRecord(value) ||
    typeof value.restaurantId !== 'string' ||
    typeof value.name !== 'string' ||
    typeof value.roadAddress !== 'string'
  ) {
    return null
  }

  return {
    restaurantId: value.restaurantId,
    name: value.name,
    roadAddress: value.roadAddress,
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value != null && !Array.isArray(value)
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    return (await response.json()) as ApiErrorBody
  } catch {
    return null
  }
}
