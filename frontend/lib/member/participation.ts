'use client'

import { authenticatedMemberFetch } from '@/lib/member/auth'
import {
  ParticipationContractError,
  parseParticipationError,
  participationErrorMessage,
  participationPayloadKey,
} from './participation-coordination'

export { parseParticipationError, participationErrorMessage, participationPayloadKey }

export type RequestKind = 'submission' | 'report'
export type TargetType = 'RESTAURANT' | 'CREATOR' | 'VIDEO' | 'VISIT_RELATIONSHIP'
export type RequestStatus = 'RECEIVED' | 'IN_REVIEW' | 'ACCEPTED' | 'REJECTED' | 'COMPLETED'
export type ReportType = 'ERROR' | 'CLOSED' | 'UNAVAILABLE' | 'WRONG_RELATIONSHIP' | 'INAPPROPRIATE_CONTENT'

export type ParticipationItem = {
  requestId: string
  targetType: TargetType
  candidate?: Record<string, unknown>
  targetId?: string
  reportType?: ReportType
  description: string
  evidenceUrl: string | null
  status: RequestStatus
  memberReason: string | null
  createdAt: string
  updatedAt: string
}

export type ParticipationPage = {
  items: ParticipationItem[]
  page: {
    number: number
    size: number
    totalElements: number
    totalPages: number
    hasNext: boolean
  }
}

export type SubmissionInput = {
  targetType: TargetType
  candidate: Record<string, string>
  description: string
  evidenceUrl?: string
}

export type ReportInput = {
  targetType: TargetType
  targetId: string
  reportType: ReportType
  description: string
  evidenceUrl?: string
}

export type ContractError = ParticipationContractError

async function json<T>(response: Response): Promise<T> {
  if (!response.ok) throw response
  return (await response.json()) as T
}

export async function createParticipation(
  kind: RequestKind,
  payload: SubmissionInput | ReportInput,
  idempotencyKey: string,
): Promise<ParticipationItem> {
  const response = await authenticatedMemberFetch(
    kind === 'submission' ? '/api/me/submissions' : '/api/me/reports',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(payload),
    },
  )
  return json<ParticipationItem>(response)
}

export async function getParticipations(
  kind: RequestKind,
  status: RequestStatus | '',
  page = 1,
): Promise<ParticipationPage> {
  const query = new URLSearchParams({ page: String(page), size: '20' })
  if (status) query.set('status', status)
  const resource = kind === 'submission' ? 'submissions' : 'reports'
  return json<ParticipationPage>(await authenticatedMemberFetch(
    `/api/me/${resource}?${query.toString()}`,
    { cache: 'no-store' },
  ))
}

export async function getParticipationDetail(
  kind: RequestKind,
  requestId: string,
): Promise<ParticipationItem> {
  const resource = kind === 'submission' ? 'submissions' : 'reports'
  return json<ParticipationItem>(await authenticatedMemberFetch(
    `/api/me/${resource}/${encodeURIComponent(requestId)}`,
    { cache: 'no-store' },
  ))
}
