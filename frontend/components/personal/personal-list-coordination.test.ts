const personalListAssert = require('node:assert/strict')
const personalListTest = require('node:test')
const {
  canNavigatePersonalList,
  createPersonalListCoordination,
  finishPersonalListDeletion,
  isCurrentPersonalListDeletion,
  previousPageAfterEmptyDeletionRefresh,
  startPersonalListDeletion,
  updatePersonalListView,
} = require('./personal-list-coordination.ts')

personalListTest('삭제 중에는 페이지 이동을 차단한다', () => {
  const initial = createPersonalListCoordination('favorites:2:20')
  const started = startPersonalListDeletion(initial)

  personalListAssert.ok(started.deletion)
  personalListAssert.equal(canNavigatePersonalList(started.coordination), false)
})

personalListTest('빠른 두 번째 삭제는 차단하고 첫 삭제 완료 뒤 허용한다', () => {
  const initial = createPersonalListCoordination('favorites:1:20')
  const first = startPersonalListDeletion(initial)
  personalListAssert.ok(first.deletion)

  const blocked = startPersonalListDeletion(first.coordination)
  personalListAssert.equal(blocked.deletion, null)

  const afterFirst = finishPersonalListDeletion(
    blocked.coordination,
    first.deletion,
  )
  const second = startPersonalListDeletion(afterFirst)
  personalListAssert.ok(second.deletion)
  personalListAssert.notEqual(second.deletion.id, first.deletion.id)
})

personalListTest('삭제 시작 뒤 view가 달라지면 stale 완료를 무시한다', () => {
  const initial = createPersonalListCoordination('favorites:1:20')
  const started = startPersonalListDeletion(initial)
  personalListAssert.ok(started.deletion)

  const moved = updatePersonalListView(
    started.coordination,
    'recent:1:20',
  )

  personalListAssert.equal(
    isCurrentPersonalListDeletion(moved, started.deletion),
    false,
  )
  personalListAssert.equal(
    previousPageAfterEmptyDeletionRefresh(moved, started.deletion, 2, 0),
    null,
  )
})

personalListTest('2페이지 이후 재조회 결과가 빈 목록이면 이전 페이지로 이동한다', () => {
  const initial = createPersonalListCoordination('recent:3:20')
  const started = startPersonalListDeletion(initial)
  personalListAssert.ok(started.deletion)

  personalListAssert.equal(
    previousPageAfterEmptyDeletionRefresh(
      started.coordination,
      started.deletion,
      3,
      0,
    ),
    2,
  )
})
