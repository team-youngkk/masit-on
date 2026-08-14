const rateLimitStateAssert = require('node:assert/strict')
const rateLimitStateTest = require('node:test')
const { initialMapRateLimitedUntil } = require('./rate-limit-state.ts')

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
