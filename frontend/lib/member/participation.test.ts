import assert from 'node:assert/strict'
import test from 'node:test'

import { allowedReportTypes, participationErrorMessage, participationPayloadKey } from './participation-coordination.ts'

test('같은 종류와 본문은 재시도에 사용할 같은 요청 지문을 만든다', () => {
  const first = participationPayloadKey('report', { targetId: 'id', candidate: { name: '맛집', address: '서울' } })
  const second = participationPayloadKey('report', { candidate: { address: '서울', name: '맛집' }, targetId: 'id' })
  assert.equal(first, second)
})

test('중복과 일일 제한 오류는 다음 행동을 안내한다', () => {
  assert.match(participationErrorMessage(409, { code: 'DUPLICATE_OPEN_REPORT' }), /내 요청 목록/)
  assert.match(participationErrorMessage(429, { code: 'DAILY_REQUEST_LIMIT_EXCEEDED' }), /내일/)
})

test('대상 유형별로 계약된 신고 유형만 노출한다', () => {
  assert.deepEqual(allowedReportTypes('RESTAURANT'), ['ERROR', 'INAPPROPRIATE_CONTENT', 'CLOSED'])
  assert.deepEqual(allowedReportTypes('VIDEO'), ['ERROR', 'INAPPROPRIATE_CONTENT', 'UNAVAILABLE'])
  assert.deepEqual(allowedReportTypes('VISIT_RELATIONSHIP'), ['ERROR', 'INAPPROPRIATE_CONTENT', 'WRONG_RELATIONSHIP'])
})
