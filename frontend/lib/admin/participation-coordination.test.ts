import assert from 'node:assert/strict'
import test from 'node:test'

import {
  allowedNextStatuses,
  refreshAfterTransitionConflict,
  updateAdminParticipationQuery,
  validateStatusUpdate,
} from './participation-coordination.ts'

test('탭이나 필터를 바꾸면 첫 페이지로 돌아간다', () => {
  const current = { kind: 'submission' as const, status: 'RECEIVED', targetType: 'RESTAURANT', page: 4 }

  assert.equal(updateAdminParticipationQuery(current, { kind: 'report' }).page, 1)
  assert.equal(updateAdminParticipationQuery(current, { status: 'ACCEPTED' }).page, 1)
  assert.equal(updateAdminParticipationQuery(current, { targetType: 'VIDEO' }).page, 1)
  assert.equal(updateAdminParticipationQuery(current, { page: 3 }).page, 3)
})

test('현재 상태에서 계약이 허용한 다음 상태만 반환한다', () => {
  assert.deepEqual(allowedNextStatuses('RECEIVED'), ['IN_REVIEW'])
  assert.deepEqual(allowedNextStatuses('IN_REVIEW'), ['ACCEPTED', 'REJECTED'])
  assert.deepEqual(allowedNextStatuses('ACCEPTED'), ['COMPLETED'])
  assert.deepEqual(allowedNextStatuses('REJECTED'), [])
})

test('반려와 완료의 공개 사유 및 완료 조치 필수값을 검증한다', () => {
  assert.deepEqual(validateStatusUpdate({
    status: 'REJECTED', memberReason: ' ', actionConfirmed: false,
    actionType: '', targetType: '', targetId: '',
  }), ['회원 공개 사유를 입력해 주세요.'])

  assert.deepEqual(validateStatusUpdate({
    status: 'COMPLETED', memberReason: '처리 완료', actionConfirmed: false,
    actionType: '', targetType: '', targetId: '',
  }), [
    '실제 데이터 조치를 완료했는지 확인해 주세요.',
    '조치 유형을 선택해 주세요.',
    '조치 대상을 선택해 주세요.',
    '조치 대상 식별자를 입력해 주세요.',
  ])
})

test('상태 충돌이면 목록과 상세를 모두 최신 상태로 다시 조회한다', async () => {
  const calls: string[] = []
  const refreshed = await refreshAfterTransitionConflict(
    'INVALID_STATUS_TRANSITION',
    async () => { calls.push('list') },
    async () => { calls.push('detail') },
  )

  assert.equal(refreshed, true)
  assert.deepEqual(calls.sort(), ['detail', 'list'])

  calls.length = 0
  assert.equal(await refreshAfterTransitionConflict('INVALID_FIELD_VALUE', async () => {}, async () => {}), false)
  assert.deepEqual(calls, [])
})
