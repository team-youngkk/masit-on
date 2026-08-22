import { parseContractError, type ParsedContractError } from './contract-error.ts'

export type ParticipationContractError = {
  code?: string
  message?: string
  traceId?: string
  resource?: { requestId?: string; status?: string } | null
}

export type ParticipationErrorDetails = ParsedContractError<ParticipationContractError>

const TARGET_TYPE_LABELS: Record<string, string> = {
  RESTAURANT: '맛집',
  CREATOR: '유튜버',
  VIDEO: '영상',
  VISIT_RELATIONSHIP: '방문 관계',
}

const STATUS_LABELS: Record<string, string> = {
  RECEIVED: '접수',
  IN_REVIEW: '검토 중',
  ACCEPTED: '승인',
  REJECTED: '반려',
  COMPLETED: '처리 완료',
}

const REPORT_TYPE_LABELS: Record<string, string> = {
  ERROR: '정보 오류',
  CLOSED: '폐업',
  UNAVAILABLE: '이용 불가',
  WRONG_RELATIONSHIP: '잘못된 연결',
  INAPPROPRIATE_CONTENT: '부적절한 콘텐츠',
}

const CANDIDATE_FIELD_LABELS: Record<string, string> = {
  name: '이름',
  roadAddress: '도로명 주소',
  channelUrl: '채널 URL',
  videoUrl: '영상 URL',
  restaurantId: '맛집 ID',
  creatorId: '유튜버 ID',
  videoId: '영상 ID',
}

export function participationTargetTypeLabel(value: string): string {
  return TARGET_TYPE_LABELS[value] ?? value
}

export function participationStatusLabel(value: string): string {
  return STATUS_LABELS[value] ?? value
}

export function participationReportTypeLabel(value: string): string {
  return REPORT_TYPE_LABELS[value] ?? value
}

export function participationCandidateFieldLabel(value: string): string {
  return CANDIDATE_FIELD_LABELS[value] ?? value
}

/**
 * createParticipation/getParticipations/getParticipationDetail은 실패 시
 * 원본 Response를 던진다. 호출부(ParticipationRequestScreen)가 매번 같은
 * 파싱을 반복하지 않도록 status/traceId/code/message를 여기서 뽑아낸다.
 */
export async function parseParticipationError(reason: unknown): Promise<ParticipationErrorDetails | null> {
  return parseContractError<ParticipationContractError>(reason)
}

export function participationDuplicateRequestId(error: ParticipationContractError): string | undefined {
  if (error.code !== 'DUPLICATE_OPEN_SUBMISSION' && error.code !== 'DUPLICATE_OPEN_REPORT') return undefined
  const requestId = error.resource?.requestId?.trim()
  return requestId || undefined
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
      .map(([key, value]) => [participationCandidateFieldLabel(key), String(value)])
  }
  return [
    ...(item.targetId ? [['대상 식별자', item.targetId] as [string, string]] : []),
    ...(item.reportType ? [['신고 유형', participationReportTypeLabel(item.reportType)] as [string, string]] : []),
  ]
}

export function participationTargetSummary(item: ParticipationTarget): string {
  const values = participationTargetDetails(item).map(([, value]) => value)
  const targetType = participationTargetTypeLabel(item.targetType)
  return values.length ? `${targetType} · ${values.join(' · ')}` : targetType
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
