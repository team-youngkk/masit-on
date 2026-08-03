'use client'

import { withMemberAuthCookieLock } from './member-auth-cookie-lock.ts'

type TokenResponse = {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

let accessToken: string | null = null
let refreshPromise: Promise<string | null> | null = null
let tokenRevision = 0

export const MEMBER_SESSION_CHANGED_EVENT = 'masit-on:member-session-changed'

function notifyMemberSessionChanged(): void {
  window.dispatchEvent(new Event(MEMBER_SESSION_CHANGED_EVENT))
}

function storeAccessToken(value: string): void {
  accessToken = value
  tokenRevision += 1
  notifyMemberSessionChanged()
}

function discardAccessToken(): void {
  accessToken = null
  tokenRevision += 1
}

async function tokenResponse(response: Response): Promise<string> {
  if (response.status !== 200) {
    throw response
  }

  const body = (await response.json()) as TokenResponse
  if (
    !body.accessToken ||
    body.tokenType !== 'Bearer' ||
    !Number.isInteger(body.expiresInSeconds) ||
    body.expiresInSeconds <= 0
  ) {
    throw new Error('Invalid authentication response')
  }

  storeAccessToken(body.accessToken)
  return body.accessToken
}

function requireStatus(response: Response, expectedStatus: number): void {
  if (response.status !== expectedStatus) {
    throw response
  }
}

export function hasMemberAccessToken(): boolean {
  return accessToken !== null
}

export function clearMemberAccessToken(): void {
  discardAccessToken()
  notifyMemberSessionChanged()
}

export async function memberLogout(): Promise<void> {
  let clearedInsideLock = false
  try {
    await withMemberAuthCookieLock(async () => {
      try {
        const token = accessToken ?? await requestMemberAccessToken(tokenRevision)
        if (!token) {
          throw new Response(null, { status: 401 })
        }

        let response = await sendAuthenticatedMemberRequest(
          '/api/auth/tokens',
          { method: 'DELETE' },
          token,
        )
        if (response.status === 401 && accessToken === token) {
          discardAccessToken()
          const renewedToken = await requestMemberAccessToken(tokenRevision)
          if (renewedToken) {
            response = await sendAuthenticatedMemberRequest(
              '/api/auth/tokens',
              { method: 'DELETE' },
              renewedToken,
            )
          }
        }
        requireStatus(response, 204)
      } finally {
        clearMemberAccessToken()
        clearedInsideLock = true
      }
    })
  } catch (error) {
    if (!clearedInsideLock) {
      clearMemberAccessToken()
    }
    throw error
  }
}

export async function memberLogin(email: string, password: string): Promise<void> {
  await withMemberAuthCookieLock(async () => {
    await tokenResponse(
      await fetch('/api/auth/tokens', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      }),
    )
  })
}

export async function memberRegister(email: string, password: string): Promise<void> {
  const response = await fetch('/api/auth/registrations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  requireStatus(response, 202)
}

export async function resendMemberEmailVerification(email: string): Promise<void> {
  const response = await fetch('/api/auth/email-verifications/resend', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  })
  requireStatus(response, 202)
}

export async function requestPasswordReset(email: string): Promise<void> {
  const response = await fetch('/api/auth/password-resets/requests', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  })
  requireStatus(response, 202)
}

export async function verifyMemberEmail(token: string): Promise<void> {
  const response = await fetch('/api/auth/email-verifications', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token }),
  })
  requireStatus(response, 204)
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  const response = await fetch('/api/auth/password-resets/confirmations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, newPassword }),
  })
  requireStatus(response, 204)
}

async function refreshMemberAccessToken(): Promise<string | null> {
  if (refreshPromise) {
    return refreshPromise
  }

  const revisionAtStart = tokenRevision
  refreshPromise = withMemberAuthCookieLock(() =>
    requestMemberAccessToken(revisionAtStart),
  ).finally(() => {
    refreshPromise = null
  })

  return refreshPromise
}

async function requestMemberAccessToken(
  revisionAtStart: number,
): Promise<string | null> {
  if (tokenRevision !== revisionAtStart) {
    return accessToken
  }

  try {
    const response = await fetch('/api/auth/tokens/refresh', {
      method: 'POST',
      credentials: 'include',
    })
    if (tokenRevision !== revisionAtStart) {
      return accessToken
    }
    if (!response.ok) {
      clearMemberAccessToken()
      return null
    }
    return tokenResponse(response)
  } catch {
    if (tokenRevision === revisionAtStart) {
      clearMemberAccessToken()
      return null
    }
    return accessToken
  }
}

function sendAuthenticatedMemberRequest(
  input: RequestInfo | URL,
  init: RequestInit,
  token: string,
): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${token}`)
  return fetch(input, {
    ...init,
    credentials: 'include',
    headers,
  })
}

export async function ensureMemberSession(): Promise<boolean> {
  if (accessToken) {
    return true
  }
  return (await refreshMemberAccessToken()) !== null
}

export async function authenticatedMemberFetch(
  input: RequestInfo | URL,
  init: RequestInit = {},
): Promise<Response> {
  let refreshed = false
  let token = accessToken
  if (!token) {
    refreshed = true
    token = await refreshMemberAccessToken()
  }
  if (!token) {
    return new Response(null, { status: 401 })
  }

  let response = await sendAuthenticatedMemberRequest(input, init, token)
  if (response.status !== 401) {
    return response
  }

  if (accessToken === token) {
    discardAccessToken()
  }
  if (refreshed) {
    notifyMemberSessionChanged()
    return response
  }

  const renewedToken = accessToken ?? await refreshMemberAccessToken()
  if (!renewedToken) {
    return response
  }

  response = await sendAuthenticatedMemberRequest(input, init, renewedToken)
  if (response.status === 401 && accessToken === renewedToken) {
    clearMemberAccessToken()
  }
  return response
}
