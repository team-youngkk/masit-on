'use client'

type TokenResponse = {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

let accessToken: string | null = null
let refreshPromise: Promise<string | null> | null = null
let tokenRevision = 0

export const MEMBER_SESSION_CHANGED_EVENT = 'masit-on:member-session-changed'
const MEMBER_AUTH_COOKIE_LOCK = 'masit-on:member-auth-cookie'

async function withMemberAuthCookieLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator === 'undefined' || !navigator.locks) {
    return task()
  }

  return navigator.locks.request(MEMBER_AUTH_COOKIE_LOCK, { mode: 'exclusive' }, task)
}

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
  try {
    const response = await authenticatedMemberFetch('/api/auth/tokens', { method: 'DELETE' })
    requireStatus(response, 204)
  } finally {
    clearMemberAccessToken()
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
  refreshPromise = withMemberAuthCookieLock(async () => {
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
  }).finally(() => {
    refreshPromise = null
  })

  return refreshPromise
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

  const send = (value: string) => {
    const headers = new Headers(init.headers)
    headers.set('Authorization', `Bearer ${value}`)
    return fetch(input, {
      ...init,
      credentials: 'include',
      headers,
    })
  }

  let response = await send(token)
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

  response = await send(renewedToken)
  if (response.status === 401 && accessToken === renewedToken) {
    clearMemberAccessToken()
  }
  return response
}
