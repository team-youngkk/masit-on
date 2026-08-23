import assert from 'node:assert/strict'
import test from 'node:test'

import { parseNewParticipationEntry, reportTargetType } from './participation-entry.ts'

test('맛집 상세 신고만 서버 컨텍스트 조회 대상으로 판별한다', () => {
  assert.deepEqual(parseNewParticipationEntry({ kind: 'report', targetType: 'RESTAURANT', targetId: 'restaurant-1' }), {
    kind: 'report',
    targetType: 'RESTAURANT',
    targetId: 'restaurant-1',
    isRestaurantReport: true,
  })
})

test('맛집 이외 신고 대상의 종류와 식별자를 그대로 보존한다', () => {
  assert.deepEqual(parseNewParticipationEntry({ kind: 'report', targetType: 'VIDEO', targetId: 'video-1' }), {
    kind: 'report',
    targetType: 'VIDEO',
    targetId: 'video-1',
    isRestaurantReport: false,
  })
})

test('검증된 맛집 신고는 대상 유형을 RESTAURANT으로 고정한다', () => {
  assert.equal(reportTargetType('CREATOR', true), 'RESTAURANT')
  assert.equal(reportTargetType('VIDEO', false), 'VIDEO')
})

test('누락되거나 알 수 없는 쿼리는 제보용 기본값으로 정규화한다', () => {
  assert.deepEqual(parseNewParticipationEntry({ kind: 'report', targetType: 'UNKNOWN', targetId: 'restaurant-1' }), {
    kind: 'report',
    targetType: 'RESTAURANT',
    targetId: 'restaurant-1',
    isRestaurantReport: false,
  })
})
