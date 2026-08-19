'use client'

import { withMemberAuthCookieLock } from './member-auth-cookie-lock.ts'

export type MemberRole = 'MEMBER' | 'ADMIN'
export type MemberSession = { id: string; email: string; role: MemberRole }
type TokenResponse = { accessToken: string; tokenType: string; expiresInSeconds: number; role: MemberRole }
export type RestoreResult = { session: MemberSession | null; unavailable: boolean }
let accessToken: string | null = null
let refreshPromise: Promise<RestoreResult> | null = null
let tokenRevision = 0
export const MEMBER_SESSION_CHANGED_EVENT = 'masit-on:member-session-changed'
function notifyMemberSessionChanged(): void { if (typeof window !== 'undefined') window.dispatchEvent(new Event(MEMBER_SESSION_CHANGED_EVENT)) }
function storeAccessToken(value: string): void { accessToken = value; tokenRevision += 1; notifyMemberSessionChanged() }
function discardAccessToken(): void { accessToken = null; tokenRevision += 1 }
async function tokenResponse(response: Response): Promise<MemberRole> {
  if (response.status !== 200) throw response
  const body = (await response.json()) as TokenResponse
  if (!body.accessToken || body.tokenType !== 'Bearer' || !Number.isInteger(body.expiresInSeconds) || body.expiresInSeconds <= 0 || (body.role !== 'MEMBER' && body.role !== 'ADMIN')) throw new Error('Invalid authentication response')
  storeAccessToken(body.accessToken)
  return body.role
}
function requireStatus(response: Response, expectedStatus: number): void { if (response.status !== expectedStatus) throw response }
export function hasMemberAccessToken(): boolean { return accessToken !== null }
export function clearMemberAccessToken(): void { discardAccessToken(); notifyMemberSessionChanged() }
function sendAuthenticatedMemberRequest(input: RequestInfo | URL, init: RequestInit, token: string): Promise<Response> { const headers = new Headers(init.headers); headers.set('Authorization', `Bearer ${token}`); return fetch(input, { ...init, credentials: 'include', headers }) }
async function currentSession(): Promise<MemberSession> {
  const token = accessToken
  if (!token) throw new Response(null, { status: 401 })
  const response = await sendAuthenticatedMemberRequest('/api/me', {}, token)
  if (!response.ok) throw response
  const body = (await response.json()) as MemberSession
  if (!body.id || !body.email || (body.role !== 'MEMBER' && body.role !== 'ADMIN')) throw new Error('Invalid current member response')
  return body
}
async function deleteCurrentMemberSession(): Promise<Response> {
  return withMemberAuthCookieLock(async () => {
    const token = accessToken
    if (!token) return new Response(null, { status: 401 })
    return sendAuthenticatedMemberRequest('/api/auth/tokens', { method: 'DELETE' }, token)
  })
}
export async function memberLogout(): Promise<void> {
  try {
    const restored = accessToken ? null : await restoreMemberSessionOnce()
    if (restored?.unavailable) throw new Response(null, { status: 503 })
    let response = await deleteCurrentMemberSession()
    if (response.status === 401) {
      clearMemberAccessToken()
      const refreshed = await restoreMemberSessionOnce()
      if (refreshed.unavailable || !refreshed.session) throw new Response(null, { status: 401 })
      response = await deleteCurrentMemberSession()
    }
    requireStatus(response, 204)
  } finally { clearMemberAccessToken() }
}
export async function memberLogin(email: string, password: string): Promise<MemberSession> { await withMemberAuthCookieLock(async () => { await tokenResponse(await fetch('/api/auth/tokens', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) })) }); try { return await currentSession() } catch (error) { if (error instanceof Response && error.status === 401) clearMemberAccessToken(); throw error } }
export async function memberRegister(email: string, password: string): Promise<void> { const r = await fetch('/api/auth/registrations', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) }); requireStatus(r, 202) }
export async function resendMemberEmailVerification(email: string): Promise<void> { const r = await fetch('/api/auth/email-verifications/resend', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email }) }); requireStatus(r, 202) }
export async function requestPasswordReset(email: string): Promise<void> { const r = await fetch('/api/auth/password-resets/requests', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email }) }); requireStatus(r, 202) }
export async function verifyMemberEmail(token: string): Promise<void> { const r = await fetch('/api/auth/email-verifications', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }) }); requireStatus(r, 204) }
export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> { const r = await fetch('/api/auth/password-resets/confirmations', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, newPassword }) }); requireStatus(r, 204) }
export async function restoreMemberSessionOnce(): Promise<RestoreResult> {
  if (accessToken) { try { return { session: await currentSession(), unavailable: false } } catch (error) { if (!(error instanceof Response) || error.status !== 401) return { session: null, unavailable: true }; clearMemberAccessToken() } }
  if (refreshPromise) return refreshPromise
  const revisionAtStart = tokenRevision
  refreshPromise = withMemberAuthCookieLock(async () => { try { const response = await fetch('/api/auth/tokens/refresh', { method: 'POST', credentials: 'include' }); if (tokenRevision !== revisionAtStart) return { session: accessToken ? await currentSession() : null, unavailable: false }; if (response.status === 401) { clearMemberAccessToken(); return { session: null, unavailable: false } }; if (!response.ok) return { session: null, unavailable: true }; await tokenResponse(response); return { session: await currentSession(), unavailable: false } } catch (error) { if (error instanceof Response && error.status === 401) { clearMemberAccessToken(); return { session: null, unavailable: false } }; return { session: null, unavailable: true } } }).finally(() => { refreshPromise = null })
  return refreshPromise
}
export async function ensureMemberSession(): Promise<MemberSession | null> { return (await restoreMemberSessionOnce()).session }
export async function authenticatedMemberFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> { let retried = false; let token = accessToken; if (!token) { const restored = await restoreMemberSessionOnce(); token = accessToken; if (!token || restored.unavailable) return new Response(null, { status: restored.unavailable ? 503 : 401 }); retried = true }; let response = await sendAuthenticatedMemberRequest(input, init, token); if (response.status !== 401) return response; if (retried) { if (accessToken === token) clearMemberAccessToken(); return response }; if (accessToken === token) discardAccessToken(); const restored = await restoreMemberSessionOnce(); if (!accessToken || restored.unavailable) return response; response = await sendAuthenticatedMemberRequest(input, init, accessToken); if (response.status === 401) clearMemberAccessToken(); return response }
