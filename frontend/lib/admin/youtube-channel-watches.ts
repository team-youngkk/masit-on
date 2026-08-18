'use client'

import { adminJson, AdminApiError, messageFor } from './api.ts'
import { normalizeYoutubeChannelWatchStatus } from './youtube-channel-watches-coordination.ts'

export type YoutubeChannelWatchSubscriptionStatus = 'UNKNOWN' | 'ACTIVE' | 'INACTIVE' | 'RENEWAL_FAILED'

export type YoutubeChannelWatchStatus = {
  enabled: boolean
  subscriptionStatus: YoutubeChannelWatchSubscriptionStatus
  lastNotificationAt: string | null
  lastRenewedAt: string | null
  lastErrorCategory: string | null
}

export type Creator = {
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

export async function getVerifiedCreators(): Promise<Creator[]> {
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
    return body.items.filter(isCreator)
  } catch {
    throw new CreatorListError('검증된 유튜버 목록을 불러오지 못했습니다.')
  }
}

export async function getYoutubeChannelWatch(creatorId: string): Promise<YoutubeChannelWatchStatus> {
  const raw = await adminJson<unknown>(
    `/api/admin/ai/youtube-channel-watches/${encodeURIComponent(creatorId)}`,
    { cache: 'no-store' },
  )
  return normalizeYoutubeChannelWatchStatus(raw)
}

export async function setYoutubeChannelWatchEnabled(
  creatorId: string,
  enabled: boolean,
): Promise<YoutubeChannelWatchStatus> {
  const raw = await adminJson<unknown>(
    `/api/admin/ai/youtube-channel-watches/${encodeURIComponent(creatorId)}`,
    { method: 'PUT', body: JSON.stringify({ enabled }) },
  )
  return normalizeYoutubeChannelWatchStatus(raw)
}

export function youtubeChannelWatchQueryKey(creatorId: string) {
  return ['admin', 'youtube-channel-watch', creatorId] as const
}

export function youtubeChannelWatchMessageFor(error: unknown): string {
  if (error instanceof CreatorListError) {
    return error.traceId
      ? `검증된 유튜버 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요. (문의 ID: ${error.traceId})`
      : '검증된 유튜버 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
  }

  if (error instanceof AdminApiError && error.code === 'CREATOR_NOT_FOUND') {
    return '선택한 유튜버를 감시 대상으로 확인하지 못했습니다. 목록을 새로고침해 주세요.'
  }

  if (error instanceof AdminApiError && error.code === 'EXTERNAL_SERVICE_ERROR') {
    return 'YouTube 구독 요청을 완료하지 못했습니다. 상태를 확인한 뒤 잠시 후 다시 시도해 주세요.'
  }

  return messageFor(error)
}

function isCreator(value: unknown): value is Creator {
  return value !== null
    && typeof value === 'object'
    && !Array.isArray(value)
    && typeof (value as { id?: unknown }).id === 'string'
    && typeof (value as { channelName?: unknown }).channelName === 'string'
}
