import assert from 'node:assert/strict'
import test from 'node:test'

import {
  normalizeYoutubeChannelWatchStatus,
  watchEnabledLabel,
  watchErrorCategoryLabel,
  watchErrorMessage,
  watchStatusPresentation,
  watchStartAllowed,
  watchToggleEnabled,
  watchToggleLabel,
} from './youtube-channel-watches-coordination.ts'

test('ACTIVE와 UNKNOWN은 Webhook 수신 가능 여부를 다르게 안내한다', () => {
  assert.equal(watchStatusPresentation('ACTIVE').label, '활성')
  assert.match(watchStatusPresentation('ACTIVE').description, /Webhook을 받을 수 있습니다/)
  assert.equal(watchStatusPresentation('UNKNOWN').label, '확인 대기')
  assert.match(watchStatusPresentation('UNKNOWN').description, /Webhook을 받지 않습니다/)
})

test('INACTIVE와 RENEWAL_FAILED는 각각 중지와 갱신 실패로 구분한다', () => {
  assert.equal(watchStatusPresentation('INACTIVE').label, '비활성')
  assert.equal(watchStatusPresentation('RENEWAL_FAILED').label, '갱신 실패')
  assert.notEqual(watchStatusPresentation('INACTIVE').tone, watchStatusPresentation('RENEWAL_FAILED').tone)
})

test('구독 오류 범주는 원문 대신 안전한 운영 안내로 매핑한다', () => {
  for (const category of ['SUBSCRIPTION_4XX', 'SUBSCRIPTION_5XX', 'SUBSCRIPTION_UNEXPECTED_STATUS', 'TIMEOUT', 'UPSTREAM']) {
    const message = watchErrorMessage(category)
    assert.ok(message)
    assert.doesNotMatch(message, new RegExp(category))
  }
  assert.equal(watchErrorMessage(null), null)
  assert.match(watchErrorMessage('UNEXPECTED_INTERNAL_VALUE') ?? '', /운영 오류/)
})

test('마지막 오류 범주는 화면용 안전한 라벨로 표시한다', () => {
  assert.equal(watchErrorCategoryLabel('SUBSCRIPTION_TIMEOUT'), '구독 요청 시간 초과')
  assert.equal(watchErrorCategoryLabel(null), '없음')
  assert.equal(watchErrorCategoryLabel('UNEXPECTED_INTERNAL_VALUE'), '분류되지 않은 오류')
})

test('응답 경계값은 허용된 상태와 안전한 nullable 필드로 정규화한다', () => {
  assert.deepEqual(normalizeYoutubeChannelWatchStatus({
    enabled: true,
    subscriptionStatus: 'ACTIVE',
    lastNotificationAt: '',
    lastRenewedAt: '2026-08-12T01:00:00Z',
    lastErrorCategory: null,
    lastErrorAt: null,
  }), {
    enabled: true,
    subscriptionStatus: 'ACTIVE',
    lastNotificationAt: null,
    lastRenewedAt: '2026-08-12T01:00:00Z',
    lastErrorCategory: null,
    lastErrorAt: null,
  })
  assert.deepEqual(normalizeYoutubeChannelWatchStatus({ enabled: 'true', subscriptionStatus: 'SECRET' }), {
    enabled: false,
    subscriptionStatus: 'UNKNOWN',
    lastNotificationAt: null,
    lastRenewedAt: null,
    lastErrorCategory: null,
    lastErrorAt: null,
  })
})

test('전환 중에도 현재 상태 기준 토글 문구를 일관되게 계산한다', () => {
  assert.equal(watchToggleLabel({ enabled: true, subscriptionStatus: 'ACTIVE', lastNotificationAt: null, lastRenewedAt: null, lastErrorCategory: null, lastErrorAt: null }), '감시 중지')
  assert.equal(watchToggleLabel({ enabled: true, subscriptionStatus: 'RENEWAL_FAILED', lastNotificationAt: null, lastRenewedAt: null, lastErrorCategory: null, lastErrorAt: null }), '감시 재시작')
  assert.equal(watchToggleEnabled({ enabled: true, subscriptionStatus: 'RENEWAL_FAILED', lastNotificationAt: null, lastRenewedAt: null, lastErrorCategory: null, lastErrorAt: null }), true)
  assert.equal(watchToggleEnabled({ enabled: true, subscriptionStatus: 'ACTIVE', lastNotificationAt: null, lastRenewedAt: null, lastErrorCategory: null, lastErrorAt: null }), false)
  assert.equal(watchToggleLabel(null), '감시 시작')
  assert.equal(watchEnabledLabel(true), '활성화 요청됨')
  assert.equal(watchEnabledLabel(false), '중지됨')
})

test('공개되고 외부 채널이 이용 가능한 행만 감시 시작을 허용한다', () => {
  assert.equal(watchStartAllowed({ publiclyVisible: true, externallyAvailable: true }), true)
  assert.equal(watchStartAllowed({ publiclyVisible: false, externallyAvailable: true }), false)
  assert.equal(watchStartAllowed({ publiclyVisible: true, externallyAvailable: false }), false)
})
