'use client'

import { adminJson, AdminApiError, messageFor } from './api.ts'
import { aiExtractionMessageForCode } from './ai-video-extractions-coordination.ts'

export type AiExecutionStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
export type AiExtractionSource = 'WEBHOOK' | 'ADMIN'
export type AiExtractionReviewStatus = 'AUTO_CONFIRMED' | 'AUTO_BLOCKED' | 'AUTO_REJECTED' | 'MANUAL_OVERRIDE'
export type AiResultCompleteness = 'COMPLETE' | 'PARTIAL'

export type AiExtractionJob = {
  jobId: string
  source: AiExtractionSource
  youtube: { channelId: string; videoId: string; videoUrl: string }
  executionStatus: AiExecutionStatus
  resultCompleteness: AiResultCompleteness | null
  reviewStatus: AiExtractionReviewStatus | null
  provider: string
  modelVersion: string
  promptVersion: string
  schemaVersion: string
  attemptCount: number
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  reused?: boolean
}

export type AiExtractionSubmissionResult = AiExtractionJob & { reused: boolean }

export type AiEvidence =
  | { type: 'TIMESTAMP'; startMs: number; endMs: number }
  | { type: 'TEXT_RANGE'; startOffset: number; endOffset: number; sourceHash: string }
  | { type: 'UNKNOWN' }

export type AiCandidate = {
  candidateTagId?: string
  field: string
  value?: string
  tagType?: string
  rawLabel?: string
  normalizedCode?: string
  label?: string
  confidence: number
  evidence: AiEvidence
}

export type AiTagDecision = { candidateTagId: string; decision: 'MANUAL_OVERRIDE'; tagCode: string | null }

export type AiManualOverrideType = 'ROLLED_BACK' | 'DISCARDED' | null

export type AiBlockReason =
  | 'PLACE_NOT_FOUND'
  | 'PLACE_AMBIGUOUS'
  | 'CATEGORY_UNRESOLVED'
  | 'MISSING_REQUIRED_FIELD'
  | 'VISIT_EVIDENCE_REQUIRED'
  | 'DUPLICATE_CONFLICT'
  | 'EXTERNAL_SERVICE_ERROR'

export type AiRecoveryPath = 'SUPPLEMENT' | 'REEXTRACT' | 'MANUAL_REGISTRATION' | 'EXISTING_RESOURCE' | 'RETRY'

export type AiRequiredSupplementField = 'kakaoPlaceUrl' | 'foodCategoryId'

export type AiPlaceDecision = { kakaoPlaceUrl: string; roadAddress: string; matchedBy: string }
export type AiCategoryDecision = { foodCategoryName: string; resolvedBy: string }

export type AiRegistrationUnit = {
  unitId: string
  restaurantName: string
  reviewStatus: AiExtractionReviewStatus
  manualOverrideType: AiManualOverrideType
  blockReason: AiBlockReason | null
  registeredRestaurantId: string | null
  registeredCreatorId: string | null
  registeredVideoId: string | null
  registeredVisitId: string | null
  reusedResources: Array<'creator' | 'video'>
  placeDecision: AiPlaceDecision | null
  categoryDecision: AiCategoryDecision | null
}

export type AiExtractionDetail = AiExtractionJob & {
  candidates: AiCandidate[]
  missingFields: string[]
  candidateTruncated: boolean
  registrationUnits: AiRegistrationUnit[]
  error: { category: string; retryable: boolean } | null
  attempts: AiExtractionAttempt[]
}

export type AiExtractionAttempt = { attemptNo: number; outcome: string; errorCategory: string | null; startedAt: string; finishedAt: string | null }

export type AiExtractionPage = {
  items: AiExtractionJob[]
  page: { number: number; size: number; totalElements: number; totalPages: number; hasNext: boolean }
}

export type AiExtractionFilters = {
  executionStatus?: AiExecutionStatus | ''
  source?: AiExtractionSource | ''
  reviewStatus?: AiExtractionReviewStatus | ''
  page: number
  size?: 10 | 20 | 50
}

export async function getAiVideoExtractions(filters: AiExtractionFilters): Promise<AiExtractionPage> {
  const query = new URLSearchParams({ page: String(filters.page), size: String(filters.size ?? 20) })
  if (filters.executionStatus) query.set('executionStatus', filters.executionStatus)
  if (filters.source) query.set('source', filters.source)
  if (filters.reviewStatus) query.set('reviewStatus', filters.reviewStatus)
  const raw = await adminJson<RawAiExtractionPage>(`/api/admin/ai/video-extractions?${query}`, { cache: 'no-store' })
  const totalPages = Math.ceil(raw.page.totalElements / raw.page.size)
  return { ...raw, page: { ...raw.page, totalPages, hasNext: raw.page.number < totalPages } }
}

export async function getAiVideoExtraction(jobId: string): Promise<AiExtractionDetail> {
  const raw = await adminJson<RawAiExtractionDetail>(`/api/admin/ai/video-extractions/${encodeURIComponent(jobId)}`, { cache: 'no-store' })
  return normalizeDetail(raw)
}

export async function createAiVideoExtraction(
  videoUrl: string,
  supplementText: string,
  idempotencyKey: string,
): Promise<AiExtractionSubmissionResult> {
  const normalizedSupplementText = supplementText.trim()
  const raw = await adminJson<AiExtractionSubmissionResult>('/api/admin/ai/video-extractions', {
    method: 'POST',
    body: JSON.stringify({
      videoUrl: videoUrl.trim(),
      ...(normalizedSupplementText ? { supplementText: normalizedSupplementText } : {}),
      ...(idempotencyKey ? { idempotencyKey } : {}),
    }),
  })
  return { ...raw, reused: raw.reused === true }
}

export async function retryAiVideoExtraction(jobId: string, supplementText: string, reason: string): Promise<AiExtractionJob> {
  return adminJson<AiExtractionJob>(`/api/admin/ai/video-extractions/${encodeURIComponent(jobId)}/retry`, {
    method: 'POST', body: JSON.stringify({ supplementText: supplementText.trim(), reason: reason.trim() }),
  })
}

export type AiReviewDecision = 'CONFIRM' | 'DISCARD' | 'ROLLBACK' | 'ADJUST_CATEGORY'
export type AiReviewSupplements = { kakaoPlaceUrl: string } | { foodCategoryId: string }

export async function reviewAiVideoExtraction(
  jobId: string,
  decision: AiReviewDecision,
  unitId: string,
  expectedReviewStatus: AiExtractionReviewStatus,
  reason: string,
  options: { supplements?: AiReviewSupplements; tagDecisions?: AiTagDecision[] } = {},
): Promise<void> {
  const { supplements, tagDecisions = [] } = options
  await adminJson<void>(`/api/admin/ai/video-extractions/${encodeURIComponent(jobId)}/review`, {
    method: 'POST',
    body: JSON.stringify({
      decision,
      unitId,
      expectedReviewStatus,
      reason: reason.trim(),
      ...(supplements ? { supplements } : {}),
      tagDecisions,
    }),
  })
}

export type AiRegistrationUnitResult = {
  unitId: string
  reviewStatus: AiExtractionReviewStatus
  restaurantId: string
  creatorId: string
  videoId: string
  visitId: string
  reusedResources: Array<'creator' | 'video'>
  placeDecision: AiPlaceDecision
  categoryDecision: AiCategoryDecision
}

export type AiValidationConflict = {
  blockReason: AiBlockReason | null
  recoveryPaths: AiRecoveryPath[]
  requiredSupplements: AiRequiredSupplementField[]
  traceId?: string
}

const BLOCK_REASONS: readonly AiBlockReason[] = [
  'PLACE_NOT_FOUND', 'PLACE_AMBIGUOUS', 'CATEGORY_UNRESOLVED', 'MISSING_REQUIRED_FIELD',
  'VISIT_EVIDENCE_REQUIRED', 'DUPLICATE_CONFLICT', 'EXTERNAL_SERVICE_ERROR',
]
const RECOVERY_PATHS: readonly AiRecoveryPath[] = ['SUPPLEMENT', 'REEXTRACT', 'MANUAL_REGISTRATION', 'EXISTING_RESOURCE', 'RETRY']
const REQUIRED_SUPPLEMENT_FIELDS: readonly AiRequiredSupplementField[] = ['kakaoPlaceUrl', 'foodCategoryId']

/** `AIEXTRACT_VALIDATION_CONFLICT` 422 응답만 등록 단위 예외 화면에 필요한 필드로 변환한다. 그 밖의 오류는 `null`이다. */
export function aiValidationConflictFrom(error: unknown): AiValidationConflict | null {
  if (!(error instanceof AdminApiError) || error.code !== 'AIEXTRACT_VALIDATION_CONFLICT') return null
  const details = error.details
  const blockReason = typeof details.blockReason === 'string' && (BLOCK_REASONS as string[]).includes(details.blockReason)
    ? details.blockReason as AiBlockReason
    : null
  const recoveryPaths = Array.isArray(details.recoveryPaths)
    ? details.recoveryPaths.filter((value): value is AiRecoveryPath => (RECOVERY_PATHS as string[]).includes(value))
    : []
  const requiredSupplements = Array.isArray(details.requiredSupplements)
    ? details.requiredSupplements.filter((value): value is AiRequiredSupplementField => (REQUIRED_SUPPLEMENT_FIELDS as string[]).includes(value))
    : []
  return { blockReason, recoveryPaths, requiredSupplements, traceId: error.traceId }
}

/** 요청 본문은 비어 있다. 이미 등록된 단위는 멱등 `200 OK`로 기존 결과를 그대로 반환한다. */
export async function registerAiRegistrationUnit(jobId: string, unitId: string): Promise<AiRegistrationUnitResult> {
  return adminJson<AiRegistrationUnitResult>(
    `/api/admin/ai/video-extractions/${encodeURIComponent(jobId)}/registration-units/${encodeURIComponent(unitId)}/registration`,
    { method: 'POST' },
  )
}

export function aiExtractionMessageFor(error: unknown, context: 'manage' | 'submission' = 'manage'): string {
  if (error instanceof AdminApiError) {
    return aiExtractionMessageForCode(error.code, context) ?? messageFor(error)
  }
  return messageFor(error)
}

type RawAiExtractionDetail = {
  jobId: string
  source: AiExtractionSource
  youtube: AiExtractionJob['youtube']
  executionStatus: AiExecutionStatus
  resultCompleteness: AiResultCompleteness | null
  reviewStatus: AiExtractionReviewStatus | null
  provider: string
  modelVersion: string
  promptVersion: string
  schemaVersion: string
  attemptCount: number
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  reused?: boolean
  candidates: unknown[]
  missingFields: unknown
  candidateTruncated?: unknown
  registrationUnits?: unknown
  error: { category: string; retryable: boolean; attemptCount: number } | null
  attempts: AiExtractionAttempt[]
}

type RawAiExtractionPage = {
  items: AiExtractionJob[]
  page: { number: number; size: number; totalElements: number }
}

function normalizeDetail(raw: RawAiExtractionDetail): AiExtractionDetail {
  const candidates = Array.isArray(raw.candidates) ? raw.candidates.map(normalizeCandidate).filter((value): value is AiCandidate => value !== null) : []
  return {
    ...raw,
    candidates,
    missingFields: Array.isArray(raw.missingFields) ? raw.missingFields.filter((value): value is string => typeof value === 'string') : [],
    candidateTruncated: raw.candidateTruncated === true,
    registrationUnits: Array.isArray(raw.registrationUnits) ? raw.registrationUnits.map(normalizeRegistrationUnit).filter((value): value is AiRegistrationUnit => value !== null) : [],
    error: raw.error ? { category: raw.error.category, retryable: raw.error.retryable } : null,
    attempts: Array.isArray(raw.attempts) ? raw.attempts : [],
  }
}

function nullableString(value: unknown): string | null {
  return typeof value === 'string' ? value : null
}

function reusedResourcesValue(value: unknown): Array<'creator' | 'video'> {
  return Array.isArray(value) ? value.filter((item): item is 'creator' | 'video' => item === 'creator' || item === 'video') : []
}

function placeDecisionValue(value: unknown): AiPlaceDecision | null {
  const raw = record(value)
  if (typeof raw.kakaoPlaceUrl !== 'string' || typeof raw.roadAddress !== 'string' || typeof raw.matchedBy !== 'string') return null
  return { kakaoPlaceUrl: raw.kakaoPlaceUrl, roadAddress: raw.roadAddress, matchedBy: raw.matchedBy }
}

function categoryDecisionValue(value: unknown): AiCategoryDecision | null {
  const raw = record(value)
  if (typeof raw.foodCategoryName !== 'string' || typeof raw.resolvedBy !== 'string') return null
  return { foodCategoryName: raw.foodCategoryName, resolvedBy: raw.resolvedBy }
}

function normalizeRegistrationUnit(value: unknown): AiRegistrationUnit | null {
  const raw = record(value)
  if (typeof raw.unitId !== 'string' || typeof raw.restaurantName !== 'string' || typeof raw.reviewStatus !== 'string') return null
  return {
    unitId: raw.unitId,
    restaurantName: raw.restaurantName,
    reviewStatus: raw.reviewStatus as AiExtractionReviewStatus,
    manualOverrideType: raw.manualOverrideType === 'ROLLED_BACK' || raw.manualOverrideType === 'DISCARDED' ? raw.manualOverrideType : null,
    blockReason: typeof raw.blockReason === 'string' && (BLOCK_REASONS as string[]).includes(raw.blockReason) ? raw.blockReason as AiBlockReason : null,
    registeredRestaurantId: nullableString(raw.registeredRestaurantId),
    registeredCreatorId: nullableString(raw.registeredCreatorId),
    registeredVideoId: nullableString(raw.registeredVideoId),
    registeredVisitId: nullableString(raw.registeredVisitId),
    reusedResources: reusedResourcesValue(raw.reusedResources),
    placeDecision: placeDecisionValue(raw.placeDecision),
    categoryDecision: categoryDecisionValue(raw.categoryDecision),
  }
}

function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function normalizeCandidate(value: unknown): AiCandidate | null {
  const raw = record(value)
  if (typeof raw.candidateTagId === 'string') return normalizeTag(value)
  if (typeof raw.field !== 'string' || typeof raw.confidence !== 'number' || !('evidence' in raw)) return null
  return { field: raw.field, value: typeof raw.value === 'string' ? raw.value : undefined, confidence: raw.confidence, evidence: evidenceValue(raw.evidence) }
}

function evidenceValue(value: unknown): AiEvidence {
  const raw = record(value)
  if (raw.type === 'TIMESTAMP' && typeof raw.startMs === 'number' && typeof raw.endMs === 'number') return { type: 'TIMESTAMP', startMs: raw.startMs, endMs: raw.endMs }
  if (raw.type === 'TEXT_RANGE' && typeof raw.startOffset === 'number' && typeof raw.endOffset === 'number' && typeof raw.sourceHash === 'string') return { type: 'TEXT_RANGE', startOffset: raw.startOffset, endOffset: raw.endOffset, sourceHash: raw.sourceHash }
  return { type: 'UNKNOWN' }
}

function normalizeTag(value: unknown): AiCandidate | null {
  const raw = record(value)
  if (typeof raw.candidateTagId !== 'string' || typeof raw.confidence !== 'number' || !('evidence' in raw)) return null
  const normalizedCode = typeof raw.normalizedCode === 'string' ? raw.normalizedCode : undefined
  const label = typeof raw.label === 'string' ? raw.label : undefined
  return {
    candidateTagId: raw.candidateTagId,
    field: typeof raw.tagType === 'string' ? raw.tagType : 'tag',
    value: label ?? normalizedCode,
    tagType: typeof raw.tagType === 'string' ? raw.tagType : undefined,
    rawLabel: typeof raw.rawLabel === 'string' ? raw.rawLabel : undefined,
    normalizedCode,
    label,
    confidence: raw.confidence,
    evidence: evidenceValue(raw.evidence),
  }
}
