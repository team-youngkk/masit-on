import assert from 'node:assert/strict'
import test from 'node:test'

import { requestCourseRoute } from './course-route-api.ts'

const SUCCESS_BODY = {
  status: 'SUCCEEDED',
  restaurants: [
    { sequence: 1, restaurantId: 'r1', name: '출발 맛집', role: 'START' },
    { sequence: 2, restaurantId: 'r2', name: '도착 맛집', role: 'DESTINATION' },
  ],
  segments: [
    { fromRestaurantId: 'r1', toRestaurantId: 'r2', distanceMeters: 4200, durationSeconds: 780 },
  ],
  totalDistanceMeters: 4200,
  totalDurationSeconds: 780,
  generatedAt: '2026-08-10T12:00:00+09:00',
  expiresAt: '2026-08-10T12:05:00+09:00',
}

test('정상 응답은 restaurantIds를 순서대로 담아 요청하고 결과를 그대로 반환한다', async (t) => {
  let requestedUrl = ''
  let requestedInit: RequestInit | undefined
  t.mock.method(
    globalThis,
    'fetch',
    async (input: RequestInfo | URL, init?: RequestInit) => {
      requestedUrl = String(input)
      requestedInit = init
      return Response.json(SUCCESS_BODY)
    },
  )

  const controller = new AbortController()
  const result = await requestCourseRoute(['r1', 'r2'], controller.signal)

  assert.deepEqual(result, { kind: 'success', route: SUCCESS_BODY })
  assert.equal(requestedUrl, '/api/restaurants/course-routes')
  assert.equal(requestedInit?.method, 'POST')
  assert.equal(requestedInit?.body, JSON.stringify({ restaurantIds: ['r1', 'r2'] }))
  assert.equal(requestedInit?.cache, 'no-store')
  assert.equal(requestedInit?.signal, controller.signal)
})

test('400 INVALID_COURSE_SIZE는 invalid 상태로 분류한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json(
      { code: 'INVALID_COURSE_SIZE', message: '선택 개수를 확인해 주세요.', traceId: 'trace-1' },
      { status: 400 },
    ),
  )

  const result = await requestCourseRoute(['r1'])
  assert.equal(result.kind, 'invalid')
  if (result.kind === 'invalid') {
    assert.equal(result.category, 'INVALID_COURSE_SIZE')
    assert.equal(result.traceId, 'trace-1')
  }
})

test('502 COURSE_ROUTE_PARTIAL_FAILURE는 failure 상태로 분류한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json(
      {
        code: 'COURSE_ROUTE_PARTIAL_FAILURE',
        message: '일부 구간의 경로 계산에 실패했습니다.',
        traceId: 'trace-2',
        details: {
          failureCategory: 'PARTIAL',
          retryGuidance: { action: 'RESELECT_OR_RETRY' },
        },
      },
      { status: 502 },
    ),
  )

  const result = await requestCourseRoute(['r1', 'r2'])
  assert.equal(result.kind, 'failure')
  if (result.kind === 'failure') {
    assert.equal(result.category, 'PARTIAL')
    assert.equal(result.retryAllowed, true)
  }
})

test('본문이 JSON이 아닌 오류 응답도 기본 메시지로 안전하게 처리한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response('gateway error', { status: 502 }))

  const result = await requestCourseRoute(['r1', 'r2'])
  assert.equal(result.kind, 'error')
})

test('계약 필드가 빠진 200 응답은 성공으로 처리하지 않는다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ status: 'SUCCEEDED' }))

  const result = await requestCourseRoute(['r1', 'r2'])
  assert.equal(result.kind, 'error')
})

test('네트워크 오류는 재시도를 안내하는 기본 메시지로 변환한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => {
    throw new TypeError('network down')
  })

  const result = await requestCourseRoute(['r1', 'r2'])
  assert.equal(result.kind, 'error')
  if (result.kind === 'error') {
    assert.match(result.message, /다시 시도/)
  }
})
