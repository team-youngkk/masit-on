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
