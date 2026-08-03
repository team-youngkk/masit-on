const mapPointsQueryAssert = require('node:assert/strict')
const mapPointsQueryTest = require('node:test')
const { buildMapPointsSearchParams, buildMapPointsQueryKey } = require('./map-points-query.ts')

mapPointsQueryTest('필터가 없으면 빈 파라미터를 반환한다', () => {
  const params = buildMapPointsSearchParams()

  mapPointsQueryAssert.equal(params.toString(), '')
})

mapPointsQueryTest('south·west·north·east는 절대 만들지 않는다', () => {
  const params = buildMapPointsSearchParams({
    query: '강된장',
    district: '마포구',
    category: '한식',
    creatorId: 'creator-1',
  })

  mapPointsQueryAssert.equal(params.has('south'), false)
  mapPointsQueryAssert.equal(params.has('west'), false)
  mapPointsQueryAssert.equal(params.has('north'), false)
  mapPointsQueryAssert.equal(params.has('east'), false)
})

mapPointsQueryTest('지정한 필터는 단일 값으로 하나씩만 보낸다', () => {
  const params = buildMapPointsSearchParams({
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
  const params = buildMapPointsSearchParams({
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
  const params = buildMapPointsSearchParams({
    query: '  강된장  ',
  })

  mapPointsQueryAssert.equal(params.get('query'), '강된장')
})

mapPointsQueryTest('buildMapPointsQueryKey는 네 필터만 순서대로 담고 south·west·north·east는 절대 포함하지 않는다', () => {
  const key = buildMapPointsQueryKey({
    query: '강된장',
    district: '마포구',
    category: '한식',
    creatorId: 'creator-1',
  })

  mapPointsQueryAssert.deepEqual(key, ['map-points', '강된장', '마포구', '한식', 'creator-1'])
})

mapPointsQueryTest(
  'buildMapPointsQueryKey는 필터가 없어도 항상 같은 길이의 키를 반환해 서버 prefetch와 클라이언트 useQuery가 같은 키를 만든다',
  () => {
    const key = buildMapPointsQueryKey()

    mapPointsQueryAssert.deepEqual(key, ['map-points', '', '', '', ''])
  },
)
