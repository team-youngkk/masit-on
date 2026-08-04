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
