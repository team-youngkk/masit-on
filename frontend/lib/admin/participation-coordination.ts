export type AdminParticipationKind = 'submission' | 'report'
export type AdminParticipationStatus =
  | 'RECEIVED'
  | 'IN_REVIEW'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'COMPLETED'

export type AdminParticipationQuery = {
  kind: AdminParticipationKind
  status: string
  targetType: string
  page: number
}

export type CompletionInput = {
  status: AdminParticipationStatus
  memberReason: string
  actionConfirmed: boolean
  actionType: string
  targetType: string
  targetId: string
}

const NEXT_STATUSES: Record<AdminParticipationStatus, AdminParticipationStatus[]> = {
  RECEIVED: ['IN_REVIEW'],
  IN_REVIEW: ['ACCEPTED', 'REJECTED'],
  ACCEPTED: ['COMPLETED'],
  REJECTED: [],
  COMPLETED: [],
}

const REFRESH_CONFLICTS = new Set([
  'INVALID_STATUS_TRANSITION',
  'SOURCE_ACTION_NOT_COMPLETED',
])

export function updateAdminParticipationQuery(
  current: AdminParticipationQuery,
  change: Partial<AdminParticipationQuery>,
): AdminParticipationQuery {
  const next = { ...current, ...change }
  const filtersChanged = next.kind !== current.kind
    || next.status !== current.status
    || next.targetType !== current.targetType
  return { ...next, page: filtersChanged ? 1 : next.page }
}

export function allowedNextStatuses(
  status: AdminParticipationStatus,
): AdminParticipationStatus[] {
  return NEXT_STATUSES[status]
}

export function validateStatusUpdate(input: CompletionInput): string[] {
  const errors: string[] = []
  const reasonLength = input.memberReason.trim().length

  if ((input.status === 'REJECTED' || input.status === 'COMPLETED') && reasonLength === 0) {
    errors.push('회원 공개 사유를 입력해 주세요.')
  }
  if (reasonLength > 1000) {
    errors.push('회원 공개 사유는 1000자 이하로 입력해 주세요.')
  }
  if (input.status === 'COMPLETED') {
    if (!input.actionConfirmed) errors.push('실제 데이터 조치를 완료했는지 확인해 주세요.')
    if (!input.actionType) errors.push('조치 유형을 선택해 주세요.')
    if (!input.targetType) errors.push('조치 대상을 선택해 주세요.')
    if (!input.targetId.trim()) errors.push('조치 대상 식별자를 입력해 주세요.')
  }

  return errors
}

export function isRefreshConflict(code?: string): boolean {
  return code !== undefined && REFRESH_CONFLICTS.has(code)
}

export async function refreshAfterTransitionConflict(
  code: string | undefined,
  refreshList: () => Promise<unknown>,
  refreshDetail: () => Promise<unknown>,
): Promise<boolean> {
  if (!isRefreshConflict(code)) return false
  await Promise.all([refreshList(), refreshDetail()])
  return true
}
