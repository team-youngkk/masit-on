'use client'

type TokenResponse = { accessToken: string; tokenType: string; expiresInSeconds: number }

let accessToken: string | null = null
let refreshPromise: Promise<string | null> | null = null

async function tokenResponse(response: Response): Promise<string> {
  if (!response.ok) throw response
  const body = (await response.json()) as TokenResponse
  if (!body.accessToken || body.tokenType !== 'Bearer') throw new Error('Invalid authentication response')
  accessToken = body.accessToken
  return body.accessToken
}

export function clearMemberAccessToken(): void {
  accessToken = null
}

export async function memberLogin(email: string, password: string): Promise<void> {
  await tokenResponse(await fetch('/api/auth/tokens', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) }))
}

export async function memberRegister(email: string, password: string): Promise<void> {
  const response = await fetch('/api/auth/registrations', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) })
  if (!response.ok) throw response
}

export async function requestPasswordReset(email: string): Promise<void> {
  const response = await fetch('/api/auth/password-resets/requests', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email }) })
  if (!response.ok) throw response
}

export async function verifyMemberEmail(token: string): Promise<void> {
  const response = await fetch('/api/auth/email-verifications', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }) })
  if (!response.ok) throw response
}

export async function confirmPasswordReset(token: string, password: string): Promise<void> {
  const response = await fetch('/api/auth/password-resets/confirmations', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, password }) })
  if (!response.ok) throw response
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
