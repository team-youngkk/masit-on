const recentViewAssert = require('node:assert/strict')
const recentViewTest = require('node:test')
const {
  coordinateRecentView,
  initialRecentViewCoordinationState,
} = require('./recent-view-coordination.ts')

recentViewTest('인증된 회원이 A를 처음 조회하면 요청을 허용한다', () => {
  const result = coordinateRecentView(
    initialRecentViewCoordinationState,
    'authenticated',
    'A',
  )

  recentViewAssert.equal(result.shouldRequest, true)
  recentViewAssert.deepEqual(result.state, { requestedRestaurantId: 'A' })
})

recentViewTest('인증된 회원이 같은 A를 다시 조회하면 중복 요청을 막는다', () => {
  const first = coordinateRecentView(
    initialRecentViewCoordinationState,
    'authenticated',
    'A',
  )

  const duplicate = coordinateRecentView(
    first.state,
    'authenticated',
    'A',
  )

  recentViewAssert.equal(duplicate.shouldRequest, false)
  recentViewAssert.deepEqual(duplicate.state, { requestedRestaurantId: 'A' })
})

recentViewTest('A 조회 직후 B로 이동하면 B 요청을 허용한다', () => {
  const first = coordinateRecentView(
    initialRecentViewCoordinationState,
    'authenticated',
    'A',
  )

  const moved = coordinateRecentView(first.state, 'authenticated', 'B')

  recentViewAssert.equal(moved.shouldRequest, true)
  recentViewAssert.deepEqual(moved.state, { requestedRestaurantId: 'B' })
})

recentViewTest('인증되지 않은 세션 상태에서는 요청 기록을 초기화한다', () => {
  const requestedA = { requestedRestaurantId: 'A' }

  for (const status of ['anonymous', 'loading', 'unavailable'] as const) {
    const reset = coordinateRecentView(requestedA, status, 'A')

    recentViewAssert.equal(reset.shouldRequest, false)
    recentViewAssert.deepEqual(reset.state, initialRecentViewCoordinationState)

    const authenticatedAgain = coordinateRecentView(
      reset.state,
      'authenticated',
      'A',
    )
    recentViewAssert.equal(authenticatedAgain.shouldRequest, true)
  }
})
