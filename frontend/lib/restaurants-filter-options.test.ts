const assert = require('node:assert/strict')
const test = require('node:test')
const { fetchRestaurantFilterOptions, fetchRegions, buildApiSearchParams, buildRestaurantsHref } = require('./restaurants-api.ts')
const { decodeRegions, regionSelection, regionLabels, changeRegionSelection } = require('./regions.ts')

const provinces = [
  { code: '1100000000', name: '서울특별시', children: [{ code: '1114000000', name: '중구' }] },
  { code: '2600000000', name: '부산광역시', children: [{ code: '2611000000', name: '중구' }] },
  { code: '3611000000', name: '세종특별자치시', children: [] },
  { code: '4100000000', name: '경기도', children: [{ code: '4111000000', name: '수원시' }] },
  { code: '5000000000', name: '제주특별자치도', children: [{ code: '5011000000', name: '제주시' }, { code: '5013000000', name: '서귀포시' }] },
]

test('전국 지역 마스터는 쿼리 없이 조회하고 자식 없는 시도와 빈 지역을 보존한다', async (t: any) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  let requestedUrl = ''
  globalThis.fetch = (async (input: RequestInfo | URL) => {
    requestedUrl = String(input)
    return Response.json({ items: provinces })
  }) as typeof fetch
  assert.deepEqual(await fetchRegions(), { ok: true, data: provinces })
  assert.equal(requestedUrl, 'http://localhost:8080/api/regions')
  assert.deepEqual(decodeRegions({ items: [] }), [])
})

test('동명 중구는 지역 코드로 구별하고 기존 district 북마크는 서울에만 연결한다', () => {
  assert.equal(regionSelection(provinces, '2611000000').province.code, '2600000000')
  assert.equal(regionSelection(provinces, '', '중구').child.code, '1114000000')
  assert.equal(regionSelection(provinces.slice(1), '', '중구').child, undefined)
  assert.equal(regionSelection(provinces, '9999999999', '중구').province, undefined)
  assert.equal(regionLabels(provinces)['2611000000'], '부산광역시 중구')
})

test('시도 변경은 이전 하위 지역과 legacy district를 지우고 세종을 직접 선택한다', () => {
  const next = changeRegionSelection('3611000000')
  assert.deepEqual(next, { regionCode: '3611000000', district: '' })
  const selected = regionSelection(provinces, next.regionCode, next.district)
  assert.equal(selected.child, undefined)
  assert.deepEqual(selected.province.children, [])
  assert.equal(regionSelection(provinces, changeRegionSelection('4100000000').regionCode).child, undefined)
  assert.equal(regionSelection(provinces, '4111000000').child.name, '수원시')
  assert.equal(regionSelection(provinces, '5013000000').province.name, '제주특별자치도')
  assert.deepEqual(changeRegionSelection(''), { regionCode: '', district: '' })
})

test('지역 응답의 잘못된 구조와 중복 코드 및 네트워크 실패를 오류로 처리한다', async (t: any) => {
  for (const invalid of [null, {}, { items: [{ code: '36', name: '세종', children: [] }] }, { items: [{ ...provinces[0], children: null }] }, { items: [provinces[0], provinces[0]] }]) {
    assert.equal(decodeRegions(invalid), null)
  }
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  globalThis.fetch = (async () => Response.json({ message: '일시 오류', traceId: 'trace-region' }, { status: 503 })) as typeof fetch
  assert.deepEqual(await fetchRegions(), { ok: false, message: '일시 오류', traceId: 'trace-region' })
  globalThis.fetch = (async () => { throw new Error('offline') }) as typeof fetch
  assert.equal((await fetchRegions()).ok, false)
})

test('지역 코드와 모든 검색 조건을 목록 요청과 페이지 이동에서 보존한다', () => {
  const params = buildApiSearchParams({ query: ' 국밥 ', regionCode: ['2611000000', '1114000000'], category: '한식', creatorId: 'creator-1', tag: 'MENU_GUKBAP', page: '3', size: '50' })
  const next = new URL(buildRestaurantsHref(params, 4), 'https://example.com').searchParams
  assert.equal(next.get('regionCode'), '2611000000')
  assert.equal(next.get('district'), null)
  assert.equal(next.get('query'), '국밥')
  assert.equal(next.get('category'), '한식')
  assert.equal(next.get('creatorId'), 'creator-1')
  assert.equal(next.get('tag'), 'MENU_GUKBAP')
  assert.equal(next.get('page'), '4')
  assert.equal(next.get('size'), '50')
  assert.equal(buildApiSearchParams({ district: '중구' }).get('district'), '중구')
  const invalid = buildApiSearchParams({ regionCode: '2611000000', district: '중구' })
  assert.equal(invalid.get('regionCode'), '2611000000')
  assert.equal(invalid.get('district'), '중구') // 충돌은 서버에서 검증하며 조용히 조건을 버리지 않는다.
})

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
