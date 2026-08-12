import type { AiExecutionStatus, AiExtractionJob, AiExtractionReviewStatus, AiExtractionSource } from './ai-video-extractions'

export type AiExtractionFilters = {
  executionStatus: AiExecutionStatus | ''
  source: AiExtractionSource | ''
  reviewStatus: AiExtractionReviewStatus | ''
  page: number
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

export function reviewActionsFor(job: Pick<AiExtractionJob, 'executionStatus' | 'resultCompleteness' | 'reviewStatus'>) {
  return {
    retry: job.executionStatus === 'FAILED' || (job.executionStatus === 'SUCCEEDED' && job.resultCompleteness === 'PARTIAL'),
    confirm: job.reviewStatus === 'AUTO_BLOCKED',
    discard: job.reviewStatus === 'AUTO_BLOCKED' || job.reviewStatus === 'AUTO_REJECTED',
    rollback: job.reviewStatus === 'AUTO_CONFIRMED',
  }
}

export function reviewRequest(decision: 'CONFIRM' | 'DISCARD' | 'ROLLBACK', expectedReviewStatus: AiExtractionReviewStatus) {
  return { decision, expectedReviewStatus }
}

export function aiExtractionMessageForCode(code?: string): string | undefined {
  switch (code) {
    case 'AIEXTRACT_DUPLICATE_CONFLICT':
      return '다른 검수 변경과 충돌했습니다. 최신 작업 상태를 다시 조회한 뒤 진행해 주세요.'
    case 'AIEXTRACT_RETRY_BLOCKED':
      return '현재 작업 상태에서는 재시도할 수 없습니다.'
    case 'AIEXTRACT_VALIDATION_CONFLICT':
      return '후보 검증에 실패해 등록 또는 검수를 완료하지 못했습니다. 최신 작업 상태를 확인해 주세요.'
    case 'AIEXTRACT_JOB_NOT_FOUND':
      return 'AI 추출 작업을 찾을 수 없습니다. 목록을 새로고침해 주세요.'
    default:
      return undefined
  }
}
