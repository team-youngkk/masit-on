'use client'

import { ensureMemberSession } from '../member/auth.ts'
import { adminJson } from './api.ts'
import {
  withinAdminScope,
  type CreateTagDefinitionRequest,
  type RestaurantVisitTags,
  type TagDefinition,
  type TagDefinitionList,
  type TagEdit,
} from './visit-tags-coordination.ts'

export function getTagDefinitions(accountId: string, signal: AbortSignal) {
  return withinAdminScope(accountId, ensureMemberSession, () => adminJson<TagDefinitionList>(
    '/api/admin/tag-definitions', { cache: 'no-store', signal },
  ), signal)
}

export function createTagDefinition(accountId: string, request: CreateTagDefinitionRequest, signal: AbortSignal) {
  return withinAdminScope(accountId, ensureMemberSession, () => adminJson<TagDefinition>(
    '/api/admin/tag-definitions', { method: 'POST', signal, body: JSON.stringify(request) },
  ), signal)
}

export function getRestaurantVisitTags(restaurantId: string, accountId: string, signal: AbortSignal) {
  return withinAdminScope(accountId, ensureMemberSession, () => adminJson<RestaurantVisitTags>(
    `/api/admin/restaurants/${encodeURIComponent(restaurantId)}/visit-tags`, { cache: 'no-store', signal },
  ), signal)
}

export function saveVisitTags(restaurantId: string, visitId: string, accountId: string, edit: TagEdit, signal: AbortSignal) {
  return withinAdminScope(accountId, ensureMemberSession, () => adminJson<void>(
    `/api/admin/restaurants/${encodeURIComponent(restaurantId)}/visits/${encodeURIComponent(visitId)}/tags`,
    { method: 'PUT', signal, body: JSON.stringify({ ...edit, reason: edit.reason.trim() }) },
  ), signal)
}
