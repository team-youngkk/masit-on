const mapPointsQueryAssert = require('node:assert/strict')
const mapPointsQueryTest = require('node:test')
const { buildMapPointsSearchParams, SEOUL_FALLBACK_BOUNDS } = require('./map-points-query.ts')

mapPointsQueryTest('영역 네 값만 있으면 필터 없이 그대로 보낸다', () => {
  const params = buildMapPointsSearchParams({
    south: 37.42,
    west: 126.76,
    north: 37.7,
    east: 127.18,
  })

  mapPointsQueryAssert.equal(params.get('south'), '37.42')
  mapPointsQueryAssert.equal(params.get('west'), '126.76')
  mapPointsQueryAssert.equal(params.get('north'), '37.7')
  mapPointsQueryAssert.equal(params.get('east'), '127.18')
  mapPointsQueryAssert.equal(params.has('query'), false)
  mapPointsQueryAssert.equal(params.has('district'), false)
  mapPointsQueryAssert.equal(params.has('category'), false)
  mapPointsQueryAssert.equal(params.has('creatorId'), false)
})

mapPointsQueryTest('지정한 필터는 단일 값으로 하나씩만 보낸다', () => {
  const params = buildMapPointsSearchParams(SEOUL_FALLBACK_BOUNDS, {
    query: '강된장',
    district: '마포구',
    category: '한식',
    creatorId: 'creator-1',
  })

  mapPointsQueryAssert.deepEqual(params.getAll('query'), ['강된장'])
  mapPointsQueryAssert.deepEqual(params.getAll('district'), ['마포구'])
  mapPointsQueryAssert.deepEqual(params.getAll('category'), ['한식'])
  mapPointsQueryAssert.deepEqual(params.getAll('creatorId'), ['creator-1'])
})

mapPointsQueryTest('빈 문자열·공백뿐인 필터는 생략한다', () => {
  const params = buildMapPointsSearchParams(SEOUL_FALLBACK_BOUNDS, {
    query: '   ',
    district: '',
    category: undefined,
    creatorId: undefined,
  })

  mapPointsQueryAssert.equal(params.has('query'), false)
  mapPointsQueryAssert.equal(params.has('district'), false)
  mapPointsQueryAssert.equal(params.has('category'), false)
  mapPointsQueryAssert.equal(params.has('creatorId'), false)
})

mapPointsQueryTest('맛집 이름 앞뒤 공백은 제거하고 보낸다', () => {
  const params = buildMapPointsSearchParams(SEOUL_FALLBACK_BOUNDS, {
    query: '  강된장  ',
  })

  mapPointsQueryAssert.equal(params.get('query'), '강된장')
})
