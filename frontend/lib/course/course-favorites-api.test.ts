import assert from 'node:assert/strict'
import test from 'node:test'

import {
  clearMemberAccessToken,
  memberLogin,
} from '../member/auth.ts'

import {
  getCourseFavorites,
  normalizeCourseFavoriteItems,
} from './course-favorites-api.ts'

class SerialLockManager {
  request<T>(_name: string, _options: { mode: 'exclusive' }, task: () => Promise<T>): Promise<T> {
    return task()
  }
}

function tokenResponse(): Response {
  return Response.json({
    accessToken: 'course-favorite-access',
    tokenType: 'Bearer',
    expiresInSeconds: 1_800,
    role: 'MEMBER',
  })
}

test('찜 응답은 코스 후보에 필요한 맛집 필드만 정규화한다', () => {
  assert.deepEqual(normalizeCourseFavoriteItems([
    { restaurant: { id: 'r1', name: '식당 A', district: '성동구', category: '한식' } },
    { restaurant: { id: '', name: '식당 B', district: '성동구', category: '한식' } },
    { restaurant: { id: 'r2', name: '식당 C' } },
    null,
  ]), [
    { id: 'r1', name: '식당 A', district: '성동구', category: '한식' },
  ])
})

test('찜 목록 페이지와 signal을 전달하고 응답을 코스 후보로 변환한다', async (t) => {
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { locks: new SerialLockManager() },
  })
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: { dispatchEvent: () => true },
  })

  let requestedUrl = ''
  let requestedInit: RequestInit | undefined
  const controller = new AbortController()
  t.mock.method(globalThis, 'fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
    requestedUrl = String(input)
    requestedInit = init
    if (requestedUrl === '/api/auth/tokens' && init?.method === 'POST') return tokenResponse()
    if (requestedUrl === '/api/me') {
      return Response.json({ id: 'member-1', email: 'member@example.com', role: 'MEMBER' })
    }
    return Response.json({
      items: [{ restaurant: { id: 'r1', name: '식당 A', district: '성동구', category: '한식' } }],
      page: { number: 2, size: 50, totalElements: 1, totalPages: 2, hasNext: true },
    })
  })

  clearMemberAccessToken()
  await memberLogin('member@example.com', 'password-value')
  const result = await getCourseFavorites(2, controller.signal)

  assert.deepEqual(result, {
    ok: true,
    items: [{ id: 'r1', name: '식당 A', district: '성동구', category: '한식' }],
    page: { number: 2, size: 50, hasNext: true },
  })
  assert.equal(requestedUrl, '/api/me/favorites?page=2&size=50')
  assert.equal(requestedInit?.signal, controller.signal)
})

test('인증이 만료된 찜 조회는 로그인 안내 상태로 변환한다', async (t) => {
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { locks: new SerialLockManager() },
  })
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: { dispatchEvent: () => true },
  })
  t.mock.method(globalThis, 'fetch', async () => new Response(null, { status: 401 }))

  clearMemberAccessToken()
  const result = await getCourseFavorites()

  assert.equal(result.ok, false)
  if (!result.ok) {
    assert.equal(result.status, 401)
    assert.match(result.message, /로그인/)
  }
})

test('찜이 없으면 오류가 아닌 빈 후보 목록을 반환한다', async (t) => {
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { locks: new SerialLockManager() },
  })
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: { dispatchEvent: () => true },
  })
  t.mock.method(globalThis, 'fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
    if (input === '/api/auth/tokens' && init?.method === 'POST') return tokenResponse()
    if (input === '/api/me') {
      return Response.json({ id: 'member-1', email: 'member@example.com', role: 'MEMBER' })
    }
    return Response.json({ items: [], page: { number: 1, size: 50, totalElements: 0, totalPages: 0, hasNext: false } })
  })

  clearMemberAccessToken()
  await memberLogin('member@example.com', 'password-value')
  assert.deepEqual(await getCourseFavorites(), {
    ok: true,
    items: [],
    page: { number: 1, size: 50, hasNext: false },
  })
})

test('찜 조회 네트워크 오류는 재시도 가능한 실패로 변환한다', async (t) => {
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { locks: new SerialLockManager() },
  })
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: { dispatchEvent: () => true },
  })
  t.mock.method(globalThis, 'fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
    if (input === '/api/auth/tokens' && init?.method === 'POST') return tokenResponse()
    if (input === '/api/me') {
      return Response.json({ id: 'member-1', email: 'member@example.com', role: 'MEMBER' })
    }
    throw new TypeError('network down')
  })

  clearMemberAccessToken()
  await memberLogin('member@example.com', 'password-value')
  const result = await getCourseFavorites()
  assert.equal(result.ok, false)
  if (!result.ok) assert.match(result.message, /다시 시도/)
})
