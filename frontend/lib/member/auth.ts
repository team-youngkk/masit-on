'use client'

type TokenResponse = { accessToken: string; tokenType: string; expiresInSeconds: number }

let accessToken: string | null = null

async function tokenResponse(response: Response): Promise<void> {
  if (!response.ok) throw response
  const body = (await response.json()) as TokenResponse
  if (!body.accessToken || body.tokenType !== 'Bearer') throw new Error('Invalid authentication response')
  accessToken = body.accessToken
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

export async function authenticatedMemberFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  if (!accessToken) {
    const refresh = await fetch('/api/auth/tokens/refresh', { method: 'POST', credentials: 'include' })
    if (!refresh.ok) return new Response(null, { status: 401 })
    await tokenResponse(refresh)
  }
  return fetch(input, { ...init, credentials: 'include', headers: { ...init.headers, Authorization: `Bearer ${accessToken}` } })
}
