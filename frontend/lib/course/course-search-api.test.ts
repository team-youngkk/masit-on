import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildCourseSearchParams,
  normalizeCourseSearchItems,
  searchCourseCandidates,
} from './course-search-api.ts'

test('빈 값·미지정 조건은 쿼리에 넣지 않고 page·size는 항상 채운다', () => {
  const params = buildCourseSearchParams({ query: '  ', district: '', category: undefined })
  assert.equal(params.toString(), 'page=1&size=20')
})

test('공백을 제거한 검색어와 지정한 필터를 쿼리에 담는다', () => {
  const params = buildCourseSearchParams({ query: ' 냉면 ', district: '마포구', category: '한식' })
  assert.equal(params.get('query'), '냉면')
  assert.equal(params.get('district'), '마포구')
  assert.equal(params.get('category'), '한식')
})

test('형식이 어긋난 항목은 걸러내고 유효한 항목만 남긴다', () => {
  const items = normalizeCourseSearchItems([
    { id: 'r1', name: '식당 A', district: '성동구', category: '한식' },
    { id: '', name: '식당 B', district: '성동구', category: '한식' },
    { id: 'r2', name: '', district: '성동구', category: '한식' },
    { id: 'r3' },
    null,
    '문자열',
  ])
  assert.deepEqual(items, [
    { id: 'r1', name: '식당 A', district: '성동구', category: '한식' },
  ])
})

test('상대 경로로 조회하고 정상 응답을 정규화한다', async (t) => {
  let requestedUrl = ''
  let requestedInit: RequestInit | undefined
  t.mock.method(
    globalThis,
    'fetch',
    async (input: RequestInfo | URL, init?: RequestInit) => {
      requestedUrl = String(input)
      requestedInit = init
      return Response.json({
        items: [{ id: 'r1', name: '식당 A', district: '성동구', category: '한식' }],
        page: { number: 1, size: 20, totalElements: 1, totalPages: 1, hasNext: false },
      })
    },
  )

  const result = await searchCourseCandidates({ query: '냉면' })

  assert.deepEqual(result, {
    ok: true,
    items: [{ id: 'r1', name: '식당 A', district: '성동구', category: '한식' }],
  })
  assert.equal(requestedUrl, '/api/restaurants?query=%EB%83%89%EB%A9%B4&page=1&size=20')
  assert.equal(requestedInit?.cache, 'no-store')
})

test('오류 응답은 서버 메시지와 traceId를 보존한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json(
      { code: 'INVALID_FIELD_VALUE', message: '조건을 확인해 주세요.', traceId: 'trace-1' },
      { status: 400 },
    ),
  )

  assert.deepEqual(await searchCourseCandidates({}), {
    ok: false,
    message: '조건을 확인해 주세요.',
    traceId: 'trace-1',
  })
})

test('네트워크 오류는 재시도를 안내하는 기본 메시지로 변환한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => {
    throw new TypeError('network down')
  })

  const result = await searchCourseCandidates({})
  assert.equal(result.ok, false)
  if (!result.ok) {
    assert.match(result.message, /다시 시도/)
  }
})

test('items가 배열이 아닌 200 응답은 오류로 닫는다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({}))

  const result = await searchCourseCandidates({})
  assert.equal(result.ok, false)
})
