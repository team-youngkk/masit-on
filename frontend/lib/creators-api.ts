export type CreatorReference = {
  id: string
  channelName: string
}

export class CreatorListError extends Error {
  readonly traceId?: string

  constructor(message: string, traceId?: string) {
    super(message)
    this.name = 'CreatorListError'
    this.traceId = traceId
  }
}

export async function fetchCreatorReferences(): Promise<CreatorReference[]> {
  let response: Response
  try {
    response = await fetch('/api/creators', { cache: 'no-store' })
  } catch {
    throw new CreatorListError('검증된 유튜버 목록을 불러오지 못했습니다.')
  }

  if (!response.ok) {
    let traceId: string | undefined
    try {
      traceId = ((await response.json()) as { traceId?: string }).traceId
    } catch {
      // 프록시가 JSON이 아닌 응답을 반환하면 traceId 없이 안내한다.
    }
    throw new CreatorListError('검증된 유튜버 목록을 불러오지 못했습니다.', traceId)
  }

  try {
    const body = (await response.json()) as { items?: unknown }
    if (!Array.isArray(body.items)) {
      throw new Error('invalid creator response')
    }
    return body.items.filter(isCreatorReference)
  } catch {
    throw new CreatorListError('검증된 유튜버 목록을 불러오지 못했습니다.')
  }
}

function isCreatorReference(value: unknown): value is CreatorReference {
  return value !== null
    && typeof value === 'object'
    && !Array.isArray(value)
    && typeof (value as { id?: unknown }).id === 'string'
    && typeof (value as { channelName?: unknown }).channelName === 'string'
}

/* 기존 Creator 상세·연결 목록 조회 계약은 그대로 유지한다. */
const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080'
const DEFAULT_SIZE = '20'
const FALLBACK_CREATOR_RESTAURANTS_ERROR_MESSAGE =
  '방문 맛집을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
const FALLBACK_CREATOR_VIDEOS_ERROR_MESSAGE =
  '근거 영상을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

export type CreatorDetail = {
  id: string
  channelName: string
  profileImageUrl: string | null
  description: string | null
  handle: string | null
  channelUrl: string
}

export type CreatorRestaurantItem = {
  id: string
  name: string
  district: string
  category: string
}

export type CreatorVideoItem = {
  id: string
  title: string
  thumbnailUrl: string
  sourceUrl: string
}

export type CreatorListPage = {
  number: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export type CreatorRestaurantsResponse = {
  items: CreatorRestaurantItem[]
  page: CreatorListPage
}

export type CreatorVideosResponse = {
  items: CreatorVideoItem[]
  page: CreatorListPage
}

type ApiErrorBody = {
  code?: string
  message?: string
  errors?: Array<{ field: string; reason: string }>
  resource?: unknown
  traceId?: string
}

export type FetchCreatorRestaurantsResult =
  | { ok: true; data: CreatorRestaurantsResponse }
  | { ok: false; message: string; traceId?: string }

export type FetchCreatorVideosResult =
  | { ok: true; data: CreatorVideosResponse }
  | { ok: false; message: string; traceId?: string }

export class CreatorNotFoundError extends Error {
  constructor(creatorId: string) {
    super(`유튜버를 찾을 수 없습니다: ${creatorId}`)
    this.name = 'CreatorNotFoundError'
  }
}

export class CreatorIdentifierInvalidError extends Error {
  constructor(creatorId: string) {
    super(`유튜버 식별자 형식이 올바르지 않습니다: ${creatorId}`)
    this.name = 'CreatorIdentifierInvalidError'
  }
}

export class CreatorDetailUnavailableError extends Error {
  constructor(
    readonly status: number,
    readonly traceId?: string,
  ) {
    super(`유튜버 상세 조회에 실패했습니다: ${status}`)
    this.name = 'CreatorDetailUnavailableError'
  }
}

export async function getCreatorDetail(creatorId: string): Promise<CreatorDetail> {
  const response = await fetch(
    `${API_BASE_URL}/api/creators/${encodeURIComponent(creatorId)}`,
    { cache: 'no-store' },
  )

  if (response.status === 404) throw new CreatorNotFoundError(creatorId)
  if (response.status === 400) throw new CreatorIdentifierInvalidError(creatorId)

  if (!response.ok) {
    let traceId: string | undefined
    try {
      traceId = ((await response.json()) as { traceId?: string }).traceId
    } catch {
      // 프록시가 만든 오류 응답처럼 본문이 JSON이 아니면 traceId 없이 안내한다.
    }
    throw new CreatorDetailUnavailableError(response.status, traceId)
  }

  return (await response.json()) as CreatorDetail
}

export async function fetchCreatorRestaurants(
  creatorId: string,
  page: number,
): Promise<FetchCreatorRestaurantsResult> {
  const params = new URLSearchParams({ page: String(page), size: DEFAULT_SIZE })
  let response: Response
  try {
    response = await fetch(
      `${API_BASE_URL}/api/creators/${encodeURIComponent(creatorId)}/restaurants?${params.toString()}`,
      { cache: 'no-store' },
    )
  } catch {
    return { ok: false, message: FALLBACK_CREATOR_RESTAURANTS_ERROR_MESSAGE }
  }

  if (!response.ok) {
    const body = await readErrorBody(response)
    return {
      ok: false,
      message: body?.message ?? FALLBACK_CREATOR_RESTAURANTS_ERROR_MESSAGE,
      traceId: body?.traceId,
    }
  }

  try {
    const data = (await response.json()) as CreatorRestaurantsResponse
    return { ok: true, data }
  } catch {
    return { ok: false, message: FALLBACK_CREATOR_RESTAURANTS_ERROR_MESSAGE }
  }
}

export async function fetchCreatorVideos(
  creatorId: string,
  page: number,
): Promise<FetchCreatorVideosResult> {
  const params = new URLSearchParams({ page: String(page), size: DEFAULT_SIZE })
  let response: Response
  try {
    response = await fetch(
      `${API_BASE_URL}/api/creators/${encodeURIComponent(creatorId)}/videos?${params.toString()}`,
      { cache: 'no-store' },
    )
  } catch {
    return { ok: false, message: FALLBACK_CREATOR_VIDEOS_ERROR_MESSAGE }
  }

  if (!response.ok) {
    const body = await readErrorBody(response)
    return {
      ok: false,
      message: body?.message ?? FALLBACK_CREATOR_VIDEOS_ERROR_MESSAGE,
      traceId: body?.traceId,
    }
  }

  try {
    const data = (await response.json()) as CreatorVideosResponse
    return { ok: true, data }
  } catch {
    return { ok: false, message: FALLBACK_CREATOR_VIDEOS_ERROR_MESSAGE }
  }
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    return (await response.json()) as ApiErrorBody
  } catch {
    return null
  }
}

const MAX_PAGE = 2147483647

export function parsePageParam(value: string | undefined): number {
  const parsed = Number(value)
  if (!Number.isSafeInteger(parsed) || parsed < 1 || parsed > MAX_PAGE) {
    return 1
  }
  return parsed
}
