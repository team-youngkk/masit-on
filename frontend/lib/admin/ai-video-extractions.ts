'use client'

import { adminJson, AdminApiError, messageFor } from './api'

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
}

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

export type AiExtractionDetail = AiExtractionJob & {
  candidates: AiCandidate[]
  missingFields: string[]
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

export async function retryAiVideoExtraction(jobId: string, supplementText: string, reason: string): Promise<AiExtractionJob> {
  return adminJson<AiExtractionJob>(`/api/admin/ai/video-extractions/${encodeURIComponent(jobId)}/retry`, {
    method: 'POST', body: JSON.stringify({ supplementText: supplementText.trim(), reason: reason.trim() }),
  })
}

export async function reviewAiVideoExtraction(
  jobId: string,
  decision: 'CONFIRM' | 'DISCARD' | 'ROLLBACK',
  expectedReviewStatus: AiExtractionReviewStatus,
  reason: string,
  tagDecisions: AiTagDecision[] = [],
): Promise<void> {
  await adminJson<void>(`/api/admin/ai/video-extractions/${encodeURIComponent(jobId)}/review`, {
    method: 'POST', body: JSON.stringify({ decision, expectedReviewStatus, reason: reason.trim(), tagDecisions }),
  })
}

export function aiExtractionMessageFor(error: unknown): string {
  if (error instanceof AdminApiError && error.status === 409) return '다른 검수 변경과 충돌했습니다. 최신 작업 상태를 다시 조회한 뒤 진행해 주세요.'
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
    error: raw.error ? { category: raw.error.category, retryable: raw.error.retryable } : null,
    attempts: Array.isArray(raw.attempts) ? raw.attempts : [],
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
