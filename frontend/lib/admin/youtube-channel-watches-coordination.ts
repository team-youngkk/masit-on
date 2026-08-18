import type { YoutubeChannelWatchStatus, YoutubeChannelWatchSubscriptionStatus } from './youtube-channel-watches.ts'

export type WatchStatusPresentation = {
  label: string
  tone: 'success' | 'neutral' | 'warning' | 'danger'
  description: string
}

const STATUS_PRESENTATIONS: Record<YoutubeChannelWatchSubscriptionStatus, WatchStatusPresentation> = {
  UNKNOWN: {
    label: '확인 대기',
    tone: 'warning',
    description: 'YouTube 구독 challenge 전이라 아직 Webhook을 받지 않습니다.',
  },
  ACTIVE: {
    label: '활성',
    tone: 'success',
    description: '구독 challenge에 성공했습니다. 신규 영상 Webhook을 받을 수 있습니다.',
  },
  INACTIVE: {
    label: '비활성',
    tone: 'neutral',
    description: '감시가 중지되어 신규 영상 Webhook을 받지 않습니다.',
  },
  RENEWAL_FAILED: {
    label: '갱신 실패',
    tone: 'danger',
    description: 'YouTube 구독 갱신에 실패해 신규 영상 Webhook을 받지 않습니다.',
  },
}

const ERROR_MESSAGES: Record<string, string> = {
  SUBSCRIPTION_4XX: 'YouTube가 구독 요청을 거부했습니다. YouTube 채널·구독 설정을 확인해 주세요.',
  SUBSCRIPTION_5XX: 'YouTube 서버에서 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.',
  SUBSCRIPTION_UNEXPECTED_STATUS: 'YouTube 구독 응답을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  SUBSCRIPTION_TIMEOUT: 'YouTube 구독 요청 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.',
  SUBSCRIPTION_UPSTREAM: 'YouTube 연결에 실패했습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요.',
  TIMEOUT: '외부 서비스 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.',
  UPSTREAM: '외부 서비스 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.',
}

const ERROR_CATEGORY_LABELS: Record<string, string> = {
  SUBSCRIPTION_4XX: '구독 요청 거부',
  SUBSCRIPTION_5XX: 'YouTube 서버 오류',
  SUBSCRIPTION_UNEXPECTED_STATUS: '예상하지 못한 구독 응답',
  SUBSCRIPTION_TIMEOUT: '구독 요청 시간 초과',
  SUBSCRIPTION_UPSTREAM: 'YouTube 연결 오류',
  TIMEOUT: '외부 서비스 응답 시간 초과',
  UPSTREAM: '외부 서비스 연결 오류',
}

export function watchStatusPresentation(status: YoutubeChannelWatchSubscriptionStatus): WatchStatusPresentation {
  return STATUS_PRESENTATIONS[status] ?? STATUS_PRESENTATIONS.UNKNOWN
}

export function watchErrorMessage(category: string | null): string | null {
  if (!category) return null
  return ERROR_MESSAGES[category] ?? '구독 처리 중 운영 오류가 발생했습니다. 상태를 확인한 뒤 다시 시도해 주세요.'
}

export function watchErrorCategoryLabel(category: string | null): string {
  if (!category) return '없음'
  return ERROR_CATEGORY_LABELS[category] ?? '분류되지 않은 오류'
}

export function watchToggleLabel(status: YoutubeChannelWatchStatus | null): string {
  return status?.enabled ? '감시 중지' : '감시 시작'
}

export function watchEnabledLabel(enabled: boolean): string {
  return enabled ? '활성화 요청됨' : '중지됨'
}

export function normalizeYoutubeChannelWatchStatus(value: unknown): YoutubeChannelWatchStatus {
  const raw = record(value)
  const subscriptionStatus = raw.subscriptionStatus
  return {
    enabled: raw.enabled === true,
    subscriptionStatus: isSubscriptionStatus(subscriptionStatus) ? subscriptionStatus : 'UNKNOWN',
    lastNotificationAt: nullableString(raw.lastNotificationAt),
    lastRenewedAt: nullableString(raw.lastRenewedAt),
    lastErrorCategory: nullableString(raw.lastErrorCategory),
  }
}

function isSubscriptionStatus(value: unknown): value is YoutubeChannelWatchSubscriptionStatus {
  return value === 'UNKNOWN' || value === 'ACTIVE' || value === 'INACTIVE' || value === 'RENEWAL_FAILED'
}

function nullableString(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null
}

function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}
