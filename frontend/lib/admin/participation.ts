'use client'

import { adminJson } from './api'
import type {
  AdminParticipationKind,
  AdminParticipationStatus,
} from './participation-coordination'

export type { AdminParticipationKind, AdminParticipationStatus }

export type AdminTargetType = 'RESTAURANT' | 'CREATOR' | 'VIDEO' | 'VISIT_RELATIONSHIP'
export type AdminActionType = 'CREATED' | 'UPDATED' | 'HIDDEN'

export type ModerationResult = {
  actionType: AdminActionType
  targetType: AdminTargetType
  targetId: string
}

export type ModerationHistory = {
  historyId: string
  adminId: string
  fromStatus: AdminParticipationStatus
  toStatus: AdminParticipationStatus
  memberReason: string | null
  internalNote: string | null
  result: ModerationResult | null
  traceId: string
  createdAt: string
}

export type AdminParticipationItem = {
  requestId: string
  memberId: string | null
  targetType: AdminTargetType
  candidate?: Record<string, unknown>
  targetId?: string
  reportType?: string
  description: string
  evidenceUrl: string | null
  status: AdminParticipationStatus
  memberReason: string | null
  internalNote: string | null
  result: ModerationResult | null
  createdAt: string
  updatedAt: string
  moderationHistory?: ModerationHistory[]
}

export type AdminParticipationPage = {
  items: AdminParticipationItem[]
  page: {
    number: number
    size: number
    totalElements: number
    totalPages: number
    hasNext: boolean
  }
}

export type StatusUpdateInput = {
  status: AdminParticipationStatus
  memberReason: string | null
  internalNote: string | null
  result: ModerationResult | null
}

function resourceFor(kind: AdminParticipationKind): string {
  return kind === 'submission' ? 'submissions' : 'reports'
}

export async function getAdminParticipations(
  kind: AdminParticipationKind,
  page: number,
  status: AdminParticipationStatus | '',
  targetType: AdminTargetType | '',
): Promise<AdminParticipationPage> {
  const query = new URLSearchParams({ page: String(page), size: '20' })
  if (status) query.set('status', status)
  if (targetType) query.set('targetType', targetType)
  return adminJson(`/api/admin/${resourceFor(kind)}?${query.toString()}`, { cache: 'no-store' })
}

export async function getAdminParticipationDetail(
  kind: AdminParticipationKind,
  requestId: string,
): Promise<AdminParticipationItem> {
  return adminJson(`/api/admin/${resourceFor(kind)}/${encodeURIComponent(requestId)}`, {
    cache: 'no-store',
  })
}

export async function updateAdminParticipationStatus(
  kind: AdminParticipationKind,
  requestId: string,
  input: StatusUpdateInput,
): Promise<AdminParticipationItem> {
  return adminJson(`/api/admin/${resourceFor(kind)}/${encodeURIComponent(requestId)}/status`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
