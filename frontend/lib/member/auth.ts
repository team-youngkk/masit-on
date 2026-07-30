'use client'

type TokenResponse = { accessToken: string; tokenType: string; expiresInSeconds: number }

let accessToken: string | null = null
let refreshPromise: Promise<string | null> | null = null

async function tokenResponse(response: Response): Promise<string> {
  if (response.status !== 200) throw response
  const body = (await response.json()) as TokenResponse
  if (!body.accessToken || body.tokenType !== 'Bearer' || !Number.isInteger(body.expiresInSeconds) || body.expiresInSeconds <= 0) {
    throw new Error('Invalid authentication response')
  }
  accessToken = body.accessToken
  return body.accessToken
}

function requireStatus(response: Response, expectedStatus: number): void {
  if (response.status !== expectedStatus) throw response
}

export function clearMemberAccessToken(): void {
  accessToken = null
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
  await tokenResponse(await fetch('/api/auth/tokens', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) }))
}

export async function memberRegister(email: string, password: string): Promise<void> {
  const response = await fetch('/api/auth/registrations', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) })
  requireStatus(response, 202)
}

export async function resendMemberEmailVerification(email: string): Promise<void> {
  const response = await fetch('/api/auth/email-verifications/resend', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email }) })
  requireStatus(response, 202)
}

export async function requestPasswordReset(email: string): Promise<void> {
  const response = await fetch('/api/auth/password-resets/requests', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email }) })
  requireStatus(response, 202)
}

export async function verifyMemberEmail(token: string): Promise<void> {
  const response = await fetch('/api/auth/email-verifications', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }) })
  requireStatus(response, 204)
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  const response = await fetch('/api/auth/password-resets/confirmations', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, newPassword }) })
  requireStatus(response, 204)
}

async function refreshMemberAccessToken(): Promise<string | null> {
  if (refreshPromise) return refreshPromise
  refreshPromise = (async () => {
    try {
      const response = await fetch('/api/auth/tokens/refresh', { method: 'POST', credentials: 'include' })
      if (!response.ok) {
        clearMemberAccessToken()
        return null
      }
      return tokenResponse(response)
    } catch {
      clearMemberAccessToken()
      return null
    }
  })().finally(() => { refreshPromise = null })
  return refreshPromise
}

export async function authenticatedMemberFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const token = accessToken ?? await refreshMemberAccessToken()
  if (!token) return new Response(null, { status: 401 })
  const send = (value: string) => fetch(input, {
    ...init,
    credentials: 'include',
    headers: { ...init.headers, Authorization: `Bearer ${value}` },
  })
  let response = await send(token)
  if (response.status !== 401) return response
  clearMemberAccessToken()
  const renewedToken = await refreshMemberAccessToken()
  if (!renewedToken) return response
  response = await send(renewedToken)
  if (response.status === 401) {
    clearMemberAccessToken()
  }
  return response
}
