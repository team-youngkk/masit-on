import type { AiExecutionStatus, AiExtractionJob, AiExtractionReviewStatus, AiExtractionSource, AiExtractionSubmissionResult } from './ai-video-extractions'
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
    ...(supplementText.trim().length <= 20_000 ? {} : { supplementText: '보완 텍스트는 공백을 제외하고 20,000자 이하로 입력해 주세요.' }),
  }
}

export function validateAiExtractionSubmission(videoUrl: string, supplementText: string): string[] {
  return Object.values(aiExtractionSubmissionFieldErrors(videoUrl, supplementText))
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
    default:
      return undefined
  }
}
