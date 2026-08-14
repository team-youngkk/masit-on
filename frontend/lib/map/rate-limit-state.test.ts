const rateLimitStateAssert = require('node:assert/strict')
const rateLimitStateTest = require('node:test')
const {
  createMapRateLimitState,
  getMapRateLimitedUntil,
  initialMapRateLimitedUntil,
  setMapRateLimitedUntil,
} = require('./rate-limit-state.ts')

const queryKeyA = ['map-points', '마포', '', '', '']
const queryKeyB = ['map-points', '강남', '', '', '']

rateLimitStateTest('서버에서 hydrate한 429의 재조회 가능 시각으로 최초 조회를 차단한다', () => {
  rateLimitStateAssert.equal(
    initialMapRateLimitedUntil({
      kind: 'rateLimited',
      retryAvailableAt: 2_000,
      message: '잠시 후 다시 시도해 주세요.',
    }),
    2_000,
  )
})

rateLimitStateTest('정상 결과와 hydrate 결과 없음은 최초 조회를 차단하지 않는다', () => {
  rateLimitStateAssert.equal(
    initialMapRateLimitedUntil({ kind: 'ok', view: { kind: 'empty' } }),
    null,
  )
  rateLimitStateAssert.equal(initialMapRateLimitedUntil(undefined), null)
})

rateLimitStateTest('hydrate한 429 대기는 같은 query key에만 적용한다', () => {
  const state = createMapRateLimitState(queryKeyA, {
    kind: 'rateLimited',
    retryAvailableAt: 5_000,
    message: '잠시 후 다시 시도해 주세요.',
  })

  rateLimitStateAssert.equal(getMapRateLimitedUntil(state, queryKeyA), 5_000)
  rateLimitStateAssert.equal(getMapRateLimitedUntil(state, queryKeyB), null)
})

rateLimitStateTest('필터를 바꿨다가 돌아오면 원래 query key의 대기를 유지한다', () => {
  const state = setMapRateLimitedUntil({}, queryKeyA, 5_000)

  rateLimitStateAssert.equal(getMapRateLimitedUntil(state, queryKeyB), null)
  rateLimitStateAssert.equal(getMapRateLimitedUntil(state, queryKeyA), 5_000)
})

rateLimitStateTest('대기 만료는 해당 query key만 제거한다', () => {
  const state = setMapRateLimitedUntil(
    setMapRateLimitedUntil({}, queryKeyA, 5_000),
    queryKeyB,
    6_000,
  )
  const cleared = setMapRateLimitedUntil(state, queryKeyA, null)

  rateLimitStateAssert.equal(getMapRateLimitedUntil(cleared, queryKeyA), null)
  rateLimitStateAssert.equal(getMapRateLimitedUntil(cleared, queryKeyB), 6_000)
})
