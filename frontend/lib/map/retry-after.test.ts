const retryAfterAssert = require('node:assert/strict')
const retryAfterTest = require('node:test')
const { parseRetryAfterHeader } = require('./retry-after.ts')

retryAfterTest('헤더가 없으면 1초 뒤를 fallback 재조회 시각으로 반환한다', () => {
  retryAfterAssert.equal(parseRetryAfterHeader(null, 1_000), 2_000)
  retryAfterAssert.equal(parseRetryAfterHeader(undefined, 1_000), 2_000)
})

retryAfterTest('정수 초를 현재 시각에 더한 시각으로 변환한다', () => {
  retryAfterAssert.equal(parseRetryAfterHeader('4', 1_000), 5_000)
})

retryAfterTest('소수 초도 밀리초로 변환한다', () => {
  retryAfterAssert.equal(parseRetryAfterHeader('0.5', 1_000), 1_500)
})

retryAfterTest('빈 값이거나 숫자가 아니거나 음수면 fallback 재조회 시각을 반환한다', () => {
  retryAfterAssert.equal(parseRetryAfterHeader('', 1_000), 2_000)
  retryAfterAssert.equal(parseRetryAfterHeader('   ', 1_000), 2_000)
  retryAfterAssert.equal(parseRetryAfterHeader('not-a-number', 1_000), 2_000)
  retryAfterAssert.equal(parseRetryAfterHeader('-1', 1_000), 2_000)
})

retryAfterTest('0초는 즉시 재조회 가능한 시각으로 변환한다', () => {
  retryAfterAssert.equal(parseRetryAfterHeader('0', 1_000), 1_000)
})
