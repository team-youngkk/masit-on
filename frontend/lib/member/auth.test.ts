const memberAuthAssert = require('node:assert/strict')
const memberAuthTest = require('node:test')
const {
  clearMemberAccessToken,
  ensureMemberSession,
  hasMemberAccessToken,
  authenticatedMemberFetch,
  memberLogin,
  memberLogout,
} = require('./auth.ts')

class SerialLockManager {
  private tail: Promise<void> = Promise.resolve()

  request<T>(
    _name: string,
    _options: { mode: 'exclusive' },
    task: () => Promise<T>,
  ): Promise<T> {
    const result = this.tail.then(task)
    this.tail = result.then(
      () => undefined,
      () => undefined,
    )
    return result
  }
}

function tokenResponse(accessToken: string): Response {
  return Response.json({
    accessToken,
    tokenType: 'Bearer',
    expiresInSeconds: 1_800,
  })
}

function deferredResponse(): {
  promise: Promise<Response>
  resolve: (response: Response) => void
} {
  let resolve!: (response: Response) => void
  const promise = new Promise<Response>((next) => {
    resolve = next
  })
  return { promise, resolve }
}

memberAuthTest('로그아웃 응답 뒤에 로그인 요청을 실행해 새 세션을 보존한다', async () => {
  const lockManager = new SerialLockManager()
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { locks: lockManager },
  })
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: { dispatchEvent: () => true },
  })

  const pendingLogout = deferredResponse()
  let loginCount = 0
  const logoutStarted = new Promise<void>((resolve) => {
    globalThis.fetch = async (input, init) => {
      const method = init?.method ?? 'GET'
      if (input === '/api/auth/tokens' && method === 'POST') {
        loginCount += 1
        return tokenResponse(`access-${loginCount}`)
      }
      if (input === '/api/auth/tokens' && method === 'DELETE') {
        resolve()
        return pendingLogout.promise
      }
      throw new Error(`Unexpected request: ${String(input)} ${method}`)
    }
  })

  clearMemberAccessToken()
  await memberLogin('member@example.com', 'password-value')
  const logout = memberLogout()
  await logoutStarted
  const nextLogin = memberLogin('member@example.com', 'password-value')

  await new Promise((resolve) => setTimeout(resolve, 0))
  memberAuthAssert.equal(hasMemberAccessToken(), true)
  memberAuthAssert.equal(loginCount, 1)

  pendingLogout.resolve(new Response(null, { status: 204 }))
  await Promise.all([logout, nextLogin])
  memberAuthAssert.equal(hasMemberAccessToken(), true)
  memberAuthAssert.equal(loginCount, 2)
})

memberAuthTest('진행 중인 refresh 뒤에 로그인을 실행해 로그인 세션을 마지막에 적용한다', async () => {
  const lockManager = new SerialLockManager()
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { locks: lockManager },
  })
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: { dispatchEvent: () => true },
  })

  const pendingRefresh = deferredResponse()
  let loginCount = 0
  let protectedAuthorization: string | null = null
  let markRefreshStarted!: () => void
  const refreshStarted = new Promise<void>((resolve) => {
    markRefreshStarted = resolve
  })
  globalThis.fetch = async (input, init) => {
    const method = init?.method ?? 'GET'
    if (input === '/api/auth/tokens/refresh' && method === 'POST') {
      markRefreshStarted()
      return pendingRefresh.promise
    }
    if (input === '/api/auth/tokens' && method === 'POST') {
      loginCount += 1
      return tokenResponse('login-access')
    }
    if (input === '/api/me' && method === 'GET') {
      protectedAuthorization = new Headers(init?.headers).get('Authorization')
      return new Response(null, { status: 200 })
    }
    throw new Error(`Unexpected request: ${String(input)} ${method}`)
  }

  clearMemberAccessToken()
  const refresh = ensureMemberSession()
  await refreshStarted
  const login = memberLogin('member@example.com', 'password-value')

  await new Promise((resolve) => setTimeout(resolve, 0))
  memberAuthAssert.equal(loginCount, 0)

  pendingRefresh.resolve(tokenResponse('refresh-access'))
  await Promise.all([refresh, login])
  await authenticatedMemberFetch('/api/me')

  memberAuthAssert.equal(loginCount, 1)
  memberAuthAssert.equal(protectedAuthorization, 'Bearer login-access')
})

memberAuthTest('메모리 토큰이 없어도 refresh 후 서버 로그아웃을 수행한다', async () => {
  const lockManager = new SerialLockManager()
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { locks: lockManager },
  })
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: { dispatchEvent: () => true },
  })

  const requests: string[] = []
  globalThis.fetch = async (input, init) => {
    const method = init?.method ?? 'GET'
    requests.push(`${method} ${String(input)}`)
    if (input === '/api/auth/tokens/refresh' && method === 'POST') {
      return tokenResponse('recovered-access')
    }
    if (input === '/api/auth/tokens' && method === 'DELETE') {
      memberAuthAssert.equal(
        new Headers(init?.headers).get('Authorization'),
        'Bearer recovered-access',
      )
      return new Response(null, { status: 204 })
    }
    throw new Error(`Unexpected request: ${String(input)} ${method}`)
  }

  clearMemberAccessToken()
  await memberLogout()

  memberAuthAssert.deepEqual(requests, [
    'POST /api/auth/tokens/refresh',
    'DELETE /api/auth/tokens',
  ])
  memberAuthAssert.equal(hasMemberAccessToken(), false)
})
