import { parseContractError, type ParsedContractError } from './contract-error.ts'

export type NotificationContractError = {
  code?: string
  message?: string
  traceId?: string
}

export type NotificationErrorDetails = ParsedContractError<NotificationContractError>

/**
 * getNotifications/getUnreadCount/markNotificationRead/markAllNotificationsRead는
 * 실패 시 원본 Response를 던진다. 호출부가 매번 같은 파싱을 반복하지 않도록
 * status/traceId/code/message를 여기서 뽑아낸다.
 */
export async function parseNotificationError(reason: unknown): Promise<NotificationErrorDetails | null> {
  return parseContractError<NotificationContractError>(reason)
}

export function notificationErrorMessage(status: number, error: NotificationContractError): string {
  if (status === 401) return '로그인이 만료되었습니다. 다시 로그인한 뒤 알림을 확인해 주세요.'
  if (error.code === 'NOTIFICATION_NOT_FOUND') return '알림을 찾을 수 없습니다.'
  return error.message || '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function formatUnreadBadge(count: number): string {
  if (count <= 0) return ''
  if (count > 99) return '99+'
  return String(count)
}

type ReadableNotification = { notificationId: string; read: boolean }

/** 개별 항목 하나의 읽음 여부만 바꾼다. 낙관적 표시(true)와 실패 롤백(false)에 모두 쓴다. */
export function setNotificationRead<T extends ReadableNotification>(
  items: T[],
  notificationId: string,
  read: boolean,
): T[] {
  return items.map((item) => (item.notificationId === notificationId ? { ...item, read } : item))
}

/** 전체 읽음 성공 시 목록의 모든 항목을 읽음으로 바꾼다. */
export function markAllItemsRead<T extends ReadableNotification>(items: T[]): T[] {
  return items.map((item) => ({ ...item, read: true }))
}

/** 알림 하나에 붙어 있던 개별 오류 안내를 지운다. 없으면 같은 참조를 그대로 돌려준다. */
export function clearItemNotice<T>(notices: Record<string, T>, notificationId: string): Record<string, T> {
  if (!(notificationId in notices)) return notices
  const next = { ...notices }
  delete next[notificationId]
  return next
}

/**
 * 목록·상세·개별 읽음 조회는 각자 요청 시퀀스 카운터(useRef)를 두고 응답이
 * 왔을 때 이 함수로 "그 사이 더 최신 요청이나 상태 변화가 없었는지"를
 * 확인한 뒤에만 상태에 반영한다. 다르면 오래된 응답이므로 버린다.
 */
export function shouldApplyResponse(requestEpoch: number, currentEpoch: number): boolean {
  return requestEpoch === currentEpoch
}
