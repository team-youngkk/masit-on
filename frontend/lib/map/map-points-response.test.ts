const mapPointsResponseAssert = require('node:assert/strict')
const mapPointsResponseTest = require('node:test')
const { classifyMapPointsResponse } = require('./map-points-response.ts')

const sampleItem = {
  id: 'restaurant-1',
  name: '강된장',
  category: '한식',
  addressSummary: '서울특별시 마포구 월드컵로 1',
  coordinate: { latitude: 37.5665, longitude: 126.978 },
}

mapPointsResponseTest('AVAILABLE이고 결과가 있으면 results로 분류한다', () => {
  const state = classifyMapPointsResponse({
    resultStatus: 'AVAILABLE',
    limit: 200,
    items: [sampleItem],
  })

  mapPointsResponseAssert.deepEqual(state, { kind: 'results', items: [sampleItem] })
})

mapPointsResponseTest('AVAILABLE이고 결과가 없으면 empty로 분류한다', () => {
  const state = classifyMapPointsResponse({
    resultStatus: 'AVAILABLE',
    limit: 200,
    items: [],
  })

  mapPointsResponseAssert.deepEqual(state, { kind: 'empty' })
})

mapPointsResponseTest('TOO_MANY_RESULTS는 items와 무관하게 tooMany로 분류한다', () => {
  const emptyItems = classifyMapPointsResponse({
    resultStatus: 'TOO_MANY_RESULTS',
    limit: 200,
    items: [],
  })
  const unexpectedItems = classifyMapPointsResponse({
    resultStatus: 'TOO_MANY_RESULTS',
    limit: 200,
    items: [sampleItem],
  })

  mapPointsResponseAssert.deepEqual(emptyItems, { kind: 'tooMany' })
  mapPointsResponseAssert.deepEqual(unexpectedItems, { kind: 'tooMany' })
})
