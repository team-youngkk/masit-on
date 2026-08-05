export type ParticipationContractError = {
  code?: string
  message?: string
  resource?: { requestId?: string; status?: string } | null
}

export function participationErrorMessage(status: number, error: ParticipationContractError): string {
  if (status === 401) return '로그인이 만료되었습니다. 다시 로그인한 뒤 입력 내용을 확인해 주세요.'
  if (error.code === 'DAILY_REQUEST_LIMIT_EXCEEDED') return '오늘 접수 가능한 5건을 모두 사용했습니다. 내일 다시 시도해 주세요.'
  if (error.code === 'DUPLICATE_OPEN_SUBMISSION' || error.code === 'DUPLICATE_OPEN_REPORT') {
    return '이미 처리 중인 같은 요청이 있습니다. 내 요청 목록에서 상태를 확인해 주세요.'
  }
  if (error.code === 'PARTICIPATION_TARGET_NOT_FOUND') return '신고 대상을 찾을 수 없습니다.'
  if (status === 400) return '입력값을 확인해 주세요. 설명은 10~2000자, 근거는 HTTPS URL만 사용할 수 있습니다.'
  return error.message || '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function participationPayloadKey(kind: string, payload: object): string {
  return `${kind}:${stableStringify(payload)}`
}

export function allowedReportTypes(targetType: string): string[] {
  const common = ['ERROR', 'INAPPROPRIATE_CONTENT']
  if (targetType === 'RESTAURANT') return [...common, 'CLOSED']
  if (targetType === 'CREATOR' || targetType === 'VIDEO') return [...common, 'UNAVAILABLE']
  if (targetType === 'VISIT_RELATIONSHIP') return [...common, 'WRONG_RELATIONSHIP']
  return common
}

type ParticipationTarget = {
  targetType: string
  candidate?: Record<string, unknown>
  targetId?: string
  reportType?: string
}

export function participationTargetDetails(item: ParticipationTarget): Array<[string, string]> {
  if (item.candidate) {
    return Object.entries(item.candidate)
      .filter((entry): entry is [string, string | number | boolean] =>
        ['string', 'number', 'boolean'].includes(typeof entry[1]))
      .map(([key, value]) => [key, String(value)])
  }
  return [
    ...(item.targetId ? [['대상 식별자', item.targetId] as [string, string]] : []),
    ...(item.reportType ? [['신고 유형', item.reportType] as [string, string]] : []),
  ]
}

export function participationTargetSummary(item: ParticipationTarget): string {
  const values = participationTargetDetails(item).map(([, value]) => value)
  return values.length ? `${item.targetType} · ${values.join(' · ')}` : item.targetType
}

export type ParticipationListQuery = {
  kind: string
  status: string
  page: number
}

export function updateParticipationListQuery(
  current: ParticipationListQuery,
  change: Partial<ParticipationListQuery>,
): ParticipationListQuery {
  const next = { ...current, ...change }
  const filterChanged = next.kind !== current.kind || next.status !== current.status
  return { ...next, page: filterChanged ? 1 : next.page }
}

function stableStringify(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`
  if (value !== null && typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, nested]) => `${JSON.stringify(key)}:${stableStringify(nested)}`)
    return `{${entries.join(',')}}`
  }
  return JSON.stringify(value)
}
