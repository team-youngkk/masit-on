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

export type YoutubeChannelWatchSummary = {
  creatorId: string
  channelName: string
  publiclyVisible: boolean
  externallyAvailable: boolean
  status: YoutubeChannelWatchStatus
}

export type YoutubeChannelWatchPage = {
  items: YoutubeChannelWatchSummary[]
  page: {
    number: number
    size: number
    totalElements: number
    totalPages: number
    hasNext: boolean
  }
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

export async function getYoutubeChannelWatches(page = 1, size = 50): Promise<YoutubeChannelWatchPage> {
  const raw = await adminJson<unknown>(
    `/api/admin/ai/youtube-channel-watches?page=${page}&size=${size}`,
    { cache: 'no-store' },
  )
  return normalizeYoutubeChannelWatchPage(raw)
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

export function youtubeChannelWatchesQueryKey(page = 1, size = 50) {
  return ['admin', 'youtube-channel-watches', page, size] as const
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

function normalizeYoutubeChannelWatchPage(value: unknown): YoutubeChannelWatchPage {
  const raw = record(value)
  const rawItems = Array.isArray(raw.items) ? raw.items : []
  const page = record(raw.page)
  return {
    items: rawItems.map((item) => {
      const summary = record(item)
      return {
        creatorId: typeof summary.creatorId === 'string' ? summary.creatorId : '',
        channelName: typeof summary.channelName === 'string' ? summary.channelName : '이름 없는 채널',
        publiclyVisible: summary.publiclyVisible === true,
        externallyAvailable: summary.externallyAvailable === true,
        status: normalizeYoutubeChannelWatchStatus(summary.status),
      }
    }).filter((item) => item.creatorId.length > 0),
    page: {
      number: numberValue(page.number, 1),
      size: numberValue(page.size, sizeFallback(rawItems.length)),
      totalElements: numberValue(page.totalElements, rawItems.length),
      totalPages: numberValue(page.totalPages, rawItems.length > 0 ? 1 : 0),
      hasNext: page.hasNext === true,
    },
  }
}

function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function numberValue(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function sizeFallback(itemCount: number): number {
  return itemCount > 0 ? itemCount : 50
}
