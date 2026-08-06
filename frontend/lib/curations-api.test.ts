import assert from 'node:assert/strict'
import test from 'node:test'

import {
  fetchPublicCuration,
  fetchPublicCurations,
} from './curations-api.ts'

test('공개 목록을 캐시하지 않고 계약된 경로에서 조회한다', async (t) => {
  let requestedUrl = ''
  let requestedInit: RequestInit | undefined
  t.mock.method(
    globalThis,
    'fetch',
    async (input: RequestInfo | URL, init?: RequestInit) => {
      requestedUrl = String(input)
      requestedInit = init
      return Response.json({ items: [] })
    },
  )

  const result = await fetchPublicCurations()

  assert.deepEqual(result, { ok: true, data: { items: [] } })
  assert.equal(requestedUrl, 'http://localhost:8080/api/curations')
  assert.equal(requestedInit?.cache, 'no-store')
})

test('상세 식별자를 형식 검증하지 않고 URL 경로 세그먼트로 전달한다', async (t) => {
  let requestedUrl = ''
  t.mock.method(globalThis, 'fetch', async (input: RequestInfo | URL) => {
    requestedUrl = String(input)
    return Response.json({
      curationId: 'opaque/id',
      title: '산책 뒤 한 끼',
      description: '설명',
      items: [],
      publishedAt: '2026-08-03T10:00:00+09:00',
      updatedAt: '2026-08-03T10:00:00+09:00',
    })
  })

  const result = await fetchPublicCuration('opaque/id')

  assert.equal(result.ok, true)
  assert.equal(requestedUrl, 'http://localhost:8080/api/curations/opaque%2Fid')
})

test('상세 404 중 CURATION_NOT_FOUND만 게시 종료 상태로 구분한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json(
      { code: 'CURATION_NOT_FOUND', message: '없음', traceId: 'trace-not-found' },
      { status: 404 },
    ),
  )

  assert.deepEqual(await fetchPublicCuration('opaque'), {
    ok: false,
    kind: 'not-found',
  })
})

test('상세 INVALID_IDENTIFIER는 찾을 수 없음 상태로 구분한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json(
      { code: 'INVALID_IDENTIFIER', message: '잘못된 식별자', traceId: 'trace-invalid' },
      { status: 400 },
    ),
  )

  assert.deepEqual(await fetchPublicCuration('not-an-identifier'), {
    ok: false,
    kind: 'not-found',
  })
})

test('다른 코드의 상세 404는 traceId가 있는 서버 오류로 유지한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json(
      { code: 'UNEXPECTED_NOT_FOUND', message: '조회 경로 오류', traceId: 'trace-404' },
      { status: 404 },
    ),
  )

  assert.deepEqual(await fetchPublicCuration('opaque'), {
    ok: false,
    kind: 'error',
    message: '조회 경로 오류',
    traceId: 'trace-404',
  })
})

test('다른 상세 오류는 서버 메시지와 traceId를 보존한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json(
      { code: 'INTERNAL_ERROR', message: '조회 실패', traceId: 'trace-500' },
      { status: 500 },
    ),
  )

  assert.deepEqual(await fetchPublicCuration('opaque'), {
    ok: false,
    kind: 'error',
    message: '조회 실패',
    traceId: 'trace-500',
  })
})

test('네트워크 오류는 사용자가 재시도할 수 있는 오류로 변환한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => {
    throw new TypeError('network down')
  })

  const result = await fetchPublicCurations()

  assert.equal(result.ok, false)
  if (!result.ok) {
    assert.match(result.message, /다시 시도/)
  }
})

test('계약 필드가 빠진 200 응답은 렌더링하지 않고 오류로 닫는다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ items: [{}] }))

  const listResult = await fetchPublicCurations()
  assert.equal(listResult.ok, false)

  t.mock.reset()
  t.mock.method(globalThis, 'fetch', async () => Response.json({}))

  assert.deepEqual(await fetchPublicCuration('opaque'), {
    ok: false,
    kind: 'error',
    message: '큐레이션을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
  })
})
