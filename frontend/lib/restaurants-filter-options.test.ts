const assert = require('node:assert/strict')
const test = require('node:test')
const { fetchRestaurantFilterOptions } = require('./restaurants-api.ts')

test('공개 맛집 필터 선택지는 지역과 음식 종류 목록을 요청한다', async (t: any) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  let requestedUrl = ''
  globalThis.fetch = (async (input: RequestInfo | URL) => {
    requestedUrl = String(input)
    return new Response(JSON.stringify({ districts: ['마포구'], categories: ['한식'] }), { status: 200 })
  }) as typeof fetch

  const result = await fetchRestaurantFilterOptions()

  assert.deepEqual(result, { ok: true, data: { districts: ['마포구'], categories: ['한식'] } })
  assert.equal(requestedUrl, 'http://localhost:8080/api/restaurants/filter-options')
})

test('필터 선택지 응답 형식이 잘못되면 안전한 오류 결과를 반환한다', async (t: any) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  globalThis.fetch = (async () => new Response(JSON.stringify({ districts: '마포구', categories: [] }), { status: 200 })) as typeof fetch

  const result = await fetchRestaurantFilterOptions()

  assert.equal(result.ok, false)
  assert.match((result as { message: string }).message, /지역·음식 종류 목록/)
})
