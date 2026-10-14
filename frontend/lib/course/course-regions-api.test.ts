import assert from 'node:assert/strict'
import test from 'node:test'

import { fetchCourseRegions } from './course-regions-api.ts'

const regions = [
  { code: '2600000000', name: '부산광역시', children: [{ code: '2611000000', name: '중구' }] },
  { code: '3611000000', name: '세종특별자치시', children: [] },
  { code: '5000000000', name: '제주특별자치도', children: [{ code: '5011000000', name: '제주시' }] },
]

test('지역 마스터는 상대 경로로 읽고 자식 없는 시도와 제주 하위 지역을 보존한다', async (t) => {
  let requestedUrl = ''
  t.mock.method(globalThis, 'fetch', async (input: RequestInfo | URL) => {
    requestedUrl = String(input)
    return Response.json({ items: regions })
  })

  assert.deepEqual(await fetchCourseRegions(), { ok: true, items: regions })
  assert.equal(requestedUrl, '/api/regions')
})

test('잘못된 지역 응답과 오류 응답은 안전한 오류 결과로 닫는다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ items: [{ code: '중구' }] }))
  assert.equal((await fetchCourseRegions()).ok, false)

  t.mock.restoreAll()
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json({ message: '지역 서버 점검 중', traceId: 'trace-region' }, { status: 503 }),
  )
  assert.deepEqual(await fetchCourseRegions(), {
    ok: false,
    message: '지역 서버 점검 중',
    traceId: 'trace-region',
  })
})

test('지역 목록 요청이 실패한 뒤 다시 요청하면 복구된 응답을 사용한다', async (t) => {
  let attempts = 0
  t.mock.method(globalThis, 'fetch', async () => {
    attempts += 1
    return attempts === 1
      ? Response.json({ message: '일시 오류' }, { status: 503 })
      : Response.json({ items: regions })
  })

  assert.equal((await fetchCourseRegions()).ok, false)
  assert.deepEqual(await fetchCourseRegions(), { ok: true, items: regions })
  assert.equal(attempts, 2)
})
