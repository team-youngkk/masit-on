const selectionSyncAssert = require('node:assert/strict')
const selectionSyncTest = require('node:test')
const {
  findSelectedMapPoint,
  isMapPointSelected,
  toggleMapSelection,
} = require('./selection-sync.ts')

selectionSyncTest('선택된 것이 없을 때 클릭하면 그 항목이 선택된다', () => {
  selectionSyncAssert.equal(toggleMapSelection(null, 'A'), 'A')
})

selectionSyncTest('다른 항목을 클릭하면 새 항목으로 선택이 바뀐다', () => {
  selectionSyncAssert.equal(toggleMapSelection('A', 'B'), 'B')
})

selectionSyncTest('선택된 항목을 다시 클릭하면 선택이 해제된다', () => {
  selectionSyncAssert.equal(toggleMapSelection('A', 'A'), null)
})

selectionSyncTest('isMapPointSelected는 선택 id와 일치할 때만 true다', () => {
  selectionSyncAssert.equal(isMapPointSelected('A', 'A'), true)
  selectionSyncAssert.equal(isMapPointSelected('A', 'B'), false)
  selectionSyncAssert.equal(isMapPointSelected('A', null), false)
})

selectionSyncTest('findSelectedMapPoint는 selectedId에 해당하는 항목을 찾는다', () => {
  const items = [{ id: 'A' }, { id: 'B' }]

  selectionSyncAssert.deepEqual(findSelectedMapPoint(items, 'B'), { id: 'B' })
  selectionSyncAssert.equal(findSelectedMapPoint(items, 'C'), null)
  selectionSyncAssert.equal(findSelectedMapPoint(items, null), null)
})
