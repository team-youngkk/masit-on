import assert from 'node:assert/strict'
import test from 'node:test'

import {
  formatUnreadBadge,
  notificationErrorMessage,
  parseNotificationError,
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
