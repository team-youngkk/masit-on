'use client'

type AccessTokenResponse = {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

let accessToken: string | null = null
let refreshPromise: Promise<string | null> | null = null

export const ADMIN_AUTH_EXPIRED_EVENT = 'masit-on:admin-auth-expired'

function storeToken(response: AccessTokenResponse): string {
  accessToken = response.accessToken
  return response.accessToken
}

async function readToken(response: Response): Promise<string> {
  const body = (await response.json()) as AccessTokenResponse
  if (!body.accessToken || body.tokenType !== 'Bearer') {
    throw new Error('관리자 인증 응답이 올바르지 않습니다.')
  }
  return storeToken(body)
}

export function hasAccessToken(): boolean {
  return accessToken !== null
}

export function clearAccessToken(): void {
  accessToken = null
}

function notifyAuthenticationExpired(): void {
  clearAccessToken()
  window.dispatchEvent(new Event(ADMIN_AUTH_EXPIRED_EVENT))
}

export async function login(loginId: string, password: string): Promise<void> {
  const response = await fetch('/api/admin/auth/tokens', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ loginId, password }),
  })

  if (!response.ok) {
    throw response
  }

  await readToken(response)
}

export async function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) {
    return refreshPromise
  }

  refreshPromise = (async () => {
    try {
      const response = await fetch('/api/admin/auth/tokens/refresh', {
        method: 'POST',
        credentials: 'include',
      })

      if (!response.ok) {
        clearAccessToken()
        return null
      }

      return readToken(response)
    } catch {
      clearAccessToken()
      return null
    }
  })().finally(() => {
    refreshPromise = null
  })

  return refreshPromise
}

export async function ensureAdminSession(): Promise<boolean> {
  if (accessToken) {
    return true
  }

  return (await refreshAccessToken()) !== null
}

export async function authenticatedFetch(
  input: RequestInfo | URL,
  init: RequestInit = {},
): Promise<Response> {
  const token = accessToken ?? (await refreshAccessToken())
  if (!token) {
    return new Response(null, { status: 401 })
  }

  const send = (value: string) =>
    fetch(input, {
      ...init,
      credentials: 'include',
      headers: {
        ...init.headers,
        Authorization: `Bearer ${value}`,
      },
    })

  let response = await send(token)
  if (response.status !== 401) {
    return response
  }

  clearAccessToken()
  const renewedToken = await refreshAccessToken()
  if (!renewedToken) {
    notifyAuthenticationExpired()
    return response
  }

  response = await send(renewedToken)
  if (response.status === 401) {
    notifyAuthenticationExpired()
  }
  return response
}

export async function logout(): Promise<void> {
  const response = await authenticatedFetch('/api/admin/auth/tokens', {
    method: 'DELETE',
  })

  if (!response.ok && response.status !== 401) {
    throw response
  }

  clearAccessToken()
}
