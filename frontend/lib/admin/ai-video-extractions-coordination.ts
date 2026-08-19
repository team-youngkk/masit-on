import type {
  AiExecutionStatus,
  AiExtractionJob,
  AiExtractionReviewStatus,
  AiExtractionSource,
  AiExtractionSubmissionResult,
  AiRecoveryPath,
  AiRegistrationUnit,
  AiReviewDecision,
  AiReviewSupplements,
} from './ai-video-extractions'
import { idempotencyAttempt, type IdempotencyAttempt } from '../idempotency.ts'

export type AiExtractionFilters = {
  executionStatus: AiExecutionStatus | ''
  source: AiExtractionSource | ''
  reviewStatus: AiExtractionReviewStatus | ''
  page: number
}

export type AiExtractionSubmissionAttempt = IdempotencyAttempt
export type AiExtractionSubmissionFieldErrors = { videoUrl?: string; supplementText?: string }

export function aiExtractionSubmissionAttempt(
  previous: AiExtractionSubmissionAttempt | null,
  fingerprint: string,
  generate: () => string,
): AiExtractionSubmissionAttempt {
  return idempotencyAttempt(previous, fingerprint, generate)
}

export function aiExtractionSubmissionFieldErrors(videoUrl: string, supplementText: string): AiExtractionSubmissionFieldErrors {
  return {
    ...(videoUrl.trim().length ? {} : { videoUrl: 'YouTube 영상 URL을 입력해 주세요.' }),
    ...(supplementText.trim().length <= 20_000 ? {} : { supplementText: '보완 텍스트는 앞뒤 공백을 제외하고 20,000자 이하로 입력해 주세요.' }),
  }
}

export function aiExtractionSubmissionPresentation(
  result: Pick<AiExtractionSubmissionResult, 'jobId' | 'executionStatus' | 'reused'>,
) {
  return {
    linkLabel: result.reused ? '기존 작업 보기' : '작업 상세 보기',
    statusLabel: result.reused ? `기존 작업 ID ${result.jobId} · 현재 상태 ${result.executionStatus}` : undefined,
  }
}

export function nextAiExtractionFilters(
  current: AiExtractionFilters,
  change: Partial<AiExtractionFilters>,
): AiExtractionFilters {
  const next = { ...current, ...change }
  return {
    ...next,
    page: change.executionStatus !== undefined || change.source !== undefined || change.reviewStatus !== undefined ? 1 : next.page,
  }
}

/** 재시도는 작업 전체 실행 상태를 대상으로 하며 등록 단위와 무관하다. */
export function retryActionAvailable(job: Pick<AiExtractionJob, 'executionStatus' | 'resultCompleteness'>): boolean {
  return job.executionStatus === 'FAILED' || (job.executionStatus === 'SUCCEEDED' && job.resultCompleteness === 'PARTIAL')
}

export type AiRegistrationUnitActions = {
  registerable: boolean
  adjustCategory: boolean
  rollback: boolean
}

/**
 * 등록 유지 상태(사후 보정 등록 완료·카테고리 보정)는 manualOverrideType이 null이면서
 * reviewStatus가 MANUAL_OVERRIDE이고 등록 결과 식별자가 모두 존재하는 조합으로 판별한다.
 * 롤백 완료·폐기 완료와 달리 별도 값을 두지 않으므로 이 조합만으로 구분한다.
 */
function isRegisteredManualOverride(unit: Pick<AiRegistrationUnit, 'reviewStatus' | 'manualOverrideType'>): boolean {
  return unit.reviewStatus === 'MANUAL_OVERRIDE' && unit.manualOverrideType === null
}

/**
 * 등록 단위별로 노출할 조치를 결정한다. 등록 실행은 AUTO_BLOCKED에서만 허용한다. AUTO_REJECTED는
 * 등록 단위 일괄 등록 API에서 항상 422 AIEXTRACT_VALIDATION_CONFLICT로 거절되는 종결 상태이므로
 * (API 3.6절 상태별 허용 범위 표), 등록 실행 버튼을 노출하지 않는다. 카테고리 보정·롤백은 등록이
 * 유지된 단위에서만 허용한다.
 */
export function registrationUnitActionsFor(
  unit: Pick<AiRegistrationUnit, 'reviewStatus' | 'manualOverrideType'>,
): AiRegistrationUnitActions {
  const registered = unit.reviewStatus === 'AUTO_CONFIRMED' || isRegisteredManualOverride(unit)
  return {
    registerable: unit.reviewStatus === 'AUTO_BLOCKED',
    adjustCategory: registered,
    rollback: registered,
  }
}

export type ExceptionAction = AiRecoveryPath | 'DISCARD'

/**
 * `DISCARD`는 `recoveryPaths` 배열과 무관한 공통 종결 동작이며 `AUTO_BLOCKED` 등록 단위에서만 허용한다.
 * `AUTO_REJECTED` 거절·롤백 완료·폐기 완료 거절은 대상이 아니므로 배열을 그대로 반환한다.
 */
export function exceptionActionsFor(unitReviewStatus: AiExtractionReviewStatus, recoveryPaths: AiRecoveryPath[]): ExceptionAction[] {
  return unitReviewStatus === 'AUTO_BLOCKED' ? [...recoveryPaths, 'DISCARD'] : [...recoveryPaths]
}

const CANDIDATE_TRUNCATED_MESSAGE = '후보 수 상한(300)을 넘어 일부 장소가 누락됐습니다.'

/** `candidateTruncated`가 true일 때만 배너 문구를 반환하고, 그 밖에는 배너를 표시하지 않는다. */
export function candidateTruncatedBannerMessage(candidateTruncated: boolean): string | null {
  return candidateTruncated ? CANDIDATE_TRUNCATED_MESSAGE : null
}

export type AiReviewRequest = {
  decision: AiReviewDecision
  unitId: string
  expectedReviewStatus: AiExtractionReviewStatus
  supplements?: AiReviewSupplements
}

export function reviewRequest(
  decision: AiReviewDecision,
  unitId: string,
  expectedReviewStatus: AiExtractionReviewStatus,
  supplements?: AiReviewSupplements,
): AiReviewRequest {
  return { decision, unitId, expectedReviewStatus, ...(supplements ? { supplements } : {}) }
}

export function aiExtractionMessageForCode(code?: string, context: 'manage' | 'submission' = 'manage'): string | undefined {
  switch (code) {
    case 'AIEXTRACT_INVALID_VIDEO_URL':
      return '공개 YouTube 영상 URL을 확인해 주세요.'
    case 'AIEXTRACT_DUPLICATE_CONFLICT':
      return context === 'submission'
        ? '이미 정식 등록된 영상과 충돌해 작업을 접수하지 못했습니다.'
        : '다른 검수 변경과 충돌했습니다. 최신 작업 상태를 다시 조회한 뒤 진행해 주세요.'
    case 'AIEXTRACT_RETRY_BLOCKED':
      return '현재 작업 상태에서는 재시도할 수 없습니다.'
    case 'AIEXTRACT_VALIDATION_CONFLICT':
      return '후보 검증에 실패해 등록 또는 검수를 완료하지 못했습니다. 최신 작업 상태를 확인해 주세요.'
    case 'AIEXTRACT_JOB_NOT_FOUND':
      return 'AI 추출 작업을 찾을 수 없습니다. 목록을 새로고침해 주세요.'
    case 'AIEXTRACT_UNIT_NOT_FOUND':
      return '등록 단위를 찾을 수 없습니다. 최신 작업 상태를 다시 조회해 주세요.'
    case 'AIEXTRACT_UNIT_ID_REQUIRED':
      return '등록 단위가 여러 개인 작업입니다. 처리할 등록 단위를 다시 선택해 주세요.'
    case 'AIEXTRACT_CONCURRENT_REQUEST_CONFLICT':
      return '같은 등록 단위에 대한 다른 요청과 충돌했습니다. 최신 상태를 다시 조회한 뒤 진행해 주세요.'
    default:
      return undefined
  }
}
