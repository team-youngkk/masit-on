import assert from 'node:assert/strict'
import test from 'node:test'

import {
  clearItemNotice,
  formatUnreadBadge,
  markAllItemsRead,
  notificationErrorMessage,
  parseNotificationError,
  setNotificationRead,
  shouldApplyResponse,
} from './notifications-coordination.ts'

test('읽지 않은 알림 배지는 0이면 숨기고 100 이상이면 99+로 표시한다', () => {
  assert.equal(formatUnreadBadge(0), '')
  assert.equal(formatUnreadBadge(1), '1')
  assert.equal(formatUnreadBadge(99), '99')
  assert.equal(formatUnreadBadge(100), '99+')
  assert.equal(formatUnreadBadge(101), '99+')
})

test('인증 만료와 알림 없음 오류는 다음 행동을 안내한다', () => {
  assert.match(notificationErrorMessage(401, {}), /로그인이 만료/)
  assert.match(notificationErrorMessage(404, { code: 'NOTIFICATION_NOT_FOUND' }), /알림을 찾을 수 없습니다/)
  assert.equal(
    notificationErrorMessage(500, { message: '서버 오류가 발생했습니다.' }),
    '서버 오류가 발생했습니다.',
  )
  assert.equal(
    notificationErrorMessage(500, {}),
    '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  )
})

test('Response 오류는 traceId를 포함한 계약 본문을 함께 반환한다', async () => {
  const response = new Response(
    JSON.stringify({ code: 'NOTIFICATION_NOT_FOUND', message: '알림을 찾을 수 없습니다.', traceId: 'trace-1' }),
    { status: 404 },
  )
  const parsed = await parseNotificationError(response)
  assert.deepEqual(parsed, {
    status: 404,
    contract: { code: 'NOTIFICATION_NOT_FOUND', message: '알림을 찾을 수 없습니다.', traceId: 'trace-1' },
  })
})

test('JSON이 아닌 오류 본문은 상태 코드만 담고 빈 계약으로 대체한다', async () => {
  const response = new Response('not-json', { status: 502 })
  const parsed = await parseNotificationError(response)
  assert.deepEqual(parsed, { status: 502, contract: {} })
})

test('Response가 아닌 원인은 네트워크 오류로 간주해 null을 반환한다', async () => {
  assert.equal(await parseNotificationError(new TypeError('network down')), null)
})

test('개별 읽음 표시는 대상 알림만 바꾸고 나머지는 그대로 둔다', () => {
  const items = [
    { notificationId: 'a', read: false },
    { notificationId: 'b', read: false },
  ]
  const afterRead = setNotificationRead(items, 'a', true)
  assert.deepEqual(afterRead, [
    { notificationId: 'a', read: true },
    { notificationId: 'b', read: false },
  ])
  assert.equal(items[0].read, false, '원본 배열은 바뀌지 않는다')

  const rolledBack = setNotificationRead(afterRead, 'a', false)
  assert.equal(rolledBack[0].read, false, '실패 롤백도 같은 함수로 되돌린다')
})

test('존재하지 않는 알림 ID로 읽음 처리를 시도하면 아무것도 바뀌지 않는다', () => {
  const items = [{ notificationId: 'a', read: false }]
  assert.deepEqual(setNotificationRead(items, 'missing', true), items)
})

test('전체 읽음은 모든 항목을 읽음으로 바꾼다', () => {
  const items = [
    { notificationId: 'a', read: false },
    { notificationId: 'b', read: true },
  ]
  assert.deepEqual(markAllItemsRead(items), [
    { notificationId: 'a', read: true },
    { notificationId: 'b', read: true },
  ])
})

test('개별 오류 안내는 해당 알림 것만 지우고, 없으면 같은 참조를 돌려준다', () => {
  const notices = { a: { text: '실패', isError: true } }
  assert.deepEqual(clearItemNotice(notices, 'a'), {})
  assert.equal(clearItemNotice(notices, 'missing'), notices)
})

test('요청 시퀀스 가드는 캡처한 값과 현재 값이 같을 때만 응답을 반영하게 한다', () => {
  assert.equal(shouldApplyResponse(1, 1), true)
  assert.equal(shouldApplyResponse(1, 2), false, '그 사이 더 최신 요청이 시작되면 오래된 응답을 버린다')
})
