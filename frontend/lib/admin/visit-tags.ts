'use client'

import { ensureMemberSession } from '../member/auth.ts'
import { adminJson } from './api.ts'
import { withinAdminScope, type RestaurantVisitTags, type TagEdit } from './visit-tags-coordination.ts'

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
