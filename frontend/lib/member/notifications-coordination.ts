export type NotificationContractError = {
  code?: string
  message?: string
  traceId?: string
}

export type NotificationErrorDetails = { status: number; contract: NotificationContractError }

/**
 * getNotifications/getUnreadCount/markNotificationRead/markAllNotificationsRead는
 * 실패 시 원본 Response를 던진다. 호출부가 매번 같은 파싱을 반복하지 않도록
 * status/traceId/code/message를 여기서 뽑아낸다.
 */
export async function parseNotificationError(reason: unknown): Promise<NotificationErrorDetails | null> {
  if (!(reason instanceof Response)) return null
  let contract: NotificationContractError = {}
  try {
    contract = (await reason.json()) as NotificationContractError
  } catch {
    contract = {}
  }
  return { status: reason.status, contract }
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
