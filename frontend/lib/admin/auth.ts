'use client'

import {
  authenticatedMemberFetch,
  clearMemberAccessToken,
  ensureMemberSession,
  hasMemberAccessToken,
  memberLogout,
  memberLogin,
} from '../member/auth.ts'

export const ADMIN_AUTH_EXPIRED_EVENT = 'masit-on:admin-auth-expired'
export const hasAccessToken = hasMemberAccessToken
export const clearAccessToken = clearMemberAccessToken
export async function ensureAdminSession(): Promise<boolean> {
  return (await ensureMemberSession())?.role === 'ADMIN'
}
export const authenticatedFetch = authenticatedMemberFetch
export const logout = memberLogout
export const login = memberLogin
