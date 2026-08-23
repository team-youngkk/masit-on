const selectionSyncAssert = require('node:assert/strict')
const selectionSyncTest = require('node:test')
const {
  findSelectedMapPoint,
  isMapPointSelected,
  toggleMapSelection,
} = require('./selection-sync.ts')
const {
  findSelectedCreatorProfileImageUrl,
} = require('./selected-creator-profile-image.ts')

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

selectionSyncTest('선택한 유튜버의 안전한 HTTPS 프로필 이미지만 반환한다', () => {
  const profileImageUrl = 'https://i.ytimg.com/channel/profile.jpg'

  selectionSyncAssert.equal(
    findSelectedCreatorProfileImageUrl('creator-1', {
      ok: true,
      data: {
        items: [
          { id: 'creator-1', channelName: '채널 하나', profileImageUrl },
          { id: 'creator-2', channelName: '채널 둘', profileImageUrl: null },
        ],
      },
    }),
    profileImageUrl,
  )
})

selectionSyncTest('목록 오류·선택 없음·일치 ID 없음·null 이미지는 기본 핀으로 폴백한다', () => {
  const success = {
    ok: true,
    data: { items: [{ id: 'creator-1', channelName: '채널 하나', profileImageUrl: null }] },
  }

  selectionSyncAssert.equal(findSelectedCreatorProfileImageUrl(undefined, success), null)
  selectionSyncAssert.equal(findSelectedCreatorProfileImageUrl('creator-2', success), null)
  selectionSyncAssert.equal(
    findSelectedCreatorProfileImageUrl('creator-1', { ok: false, message: '목록 조회 실패' }),
    null,
  )
  selectionSyncAssert.equal(findSelectedCreatorProfileImageUrl('creator-1', success), null)
})

selectionSyncTest('HTTPS가 아니거나 잘못된 외부 프로필 URL은 기본 핀으로 폴백한다', () => {
  for (const profileImageUrl of [
    'http://i.ytimg.com/channel/profile.jpg',
    'javascript:alert(1)',
    '//i.ytimg.com/channel/profile.jpg',
    'https://user:password@i.ytimg.com/channel/profile.jpg',
    'not a url',
  ]) {
    selectionSyncAssert.equal(
      findSelectedCreatorProfileImageUrl('creator-1', {
        ok: true,
        data: { items: [{ id: 'creator-1', channelName: '채널 하나', profileImageUrl }] },
      }),
      null,
    )
  }
})

selectionSyncTest('profileImageUrl 필드가 없는 구버전 목록도 기본 핀으로 폴백한다', () => {
  selectionSyncAssert.equal(
    findSelectedCreatorProfileImageUrl('creator-1', {
      ok: true,
      data: { items: [{ id: 'creator-1', channelName: '채널 하나' }] },
    }),
    null,
  )
})
