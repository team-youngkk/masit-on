'use client'

import { adminJson, AdminApiError, messageFor } from './api.ts'
import { normalizeYoutubeChannelWatchStatus } from './youtube-channel-watches-coordination.ts'
import { CreatorListError, fetchCreatorReferences } from '../creators-api.ts'
import { youtubeChannelWatchMessageForCode } from './youtube-channel-watches-coordination.ts'

export type YoutubeChannelWatchSubscriptionStatus = 'UNKNOWN' | 'ACTIVE' | 'INACTIVE' | 'RENEWAL_FAILED'

export type YoutubeChannelWatchStatus = {
  enabled: boolean
  subscriptionStatus: YoutubeChannelWatchSubscriptionStatus
  lastNotificationAt: string | null
  lastRenewedAt: string | null
  lastErrorCategory: string | null
  lastErrorAt: string | null
}

export type Creator = Awaited<ReturnType<typeof fetchCreatorReferences>>[number]

export async function getVerifiedCreators(): Promise<Creator[]> {
  return fetchCreatorReferences()
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

  if (error instanceof AdminApiError) {
    return youtubeChannelWatchMessageForCode(error.code) ?? messageFor(error)
  }

  return messageFor(error)
}

export { CreatorListError } from '../creators-api.ts'
