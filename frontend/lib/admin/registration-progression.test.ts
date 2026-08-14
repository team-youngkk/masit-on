import assert from 'node:assert/strict'
import test from 'node:test'

import { registrationCompletionTransition, registrationStepDecision } from './registration-progression.ts'

test('READY 판정은 새로 생성한다', () => {
  assert.deepEqual(registrationStepDecision({ decision: 'READY', existingResource: null }), { action: 'create' })
})

test('DUPLICATE 판정은 기존 자원 id로 건너뛴다', () => {
  assert.deepEqual(
    registrationStepDecision({ decision: 'DUPLICATE', existingResource: { id: 'existing-1', name: '기존 자원' } }),
    { action: 'skip', existingId: 'existing-1' },
  )
})

test('REVIEW_REQUIRED 판정은 진행을 막는다', () => {
  assert.deepEqual(registrationStepDecision({ decision: 'REVIEW_REQUIRED', existingResource: null }), { action: 'blocked' })
})

test('DUPLICATE인데 기존 자원 id를 확인할 수 없으면 진행을 막는다', () => {
  assert.deepEqual(registrationStepDecision({ decision: 'DUPLICATE', existingResource: null }), { action: 'blocked' })
  assert.deepEqual(registrationStepDecision({ decision: 'DUPLICATE', existingResource: { name: '이름만 있음' } }), { action: 'blocked' })
  assert.deepEqual(registrationStepDecision({ decision: 'DUPLICATE', existingResource: { id: '   ' } }), { action: 'blocked' })
})

test('DUPLICATE 자원 id의 앞뒤 공백을 제거한다', () => {
  assert.deepEqual(registrationStepDecision({ decision: 'DUPLICATE', existingResource: { id: ' existing-1 ' } }), {
    action: 'skip', existingId: 'existing-1',
  })
})

test('정상 등록은 맛집-유튜버-영상-방문 순서로 한 단계씩 진행한다', () => {
  const restaurant = registrationCompletionTransition('restaurant', { status: 'success', resourceId: 'r-1', kind: 'created' }, false)
  const creator = registrationCompletionTransition('creator', { status: 'success', resourceId: 'c-1', kind: 'created' }, false)
  const video = registrationCompletionTransition('video', { status: 'success', resourceId: 'v-1', kind: 'created' }, false)
  assert.deepEqual(restaurant, { resourceId: 'r-1', nextStep: 'creator' })
  assert.deepEqual(creator, { resourceId: 'c-1', nextStep: 'video' })
  assert.deepEqual(video, { resourceId: 'v-1', nextStep: 'visit' })
})

test('DUPLICATE 완료는 다음 단계로 정확히 한 번만 진행하고 실패는 진행시키지 않는다', () => {
  const first = registrationCompletionTransition('restaurant', { status: 'success', resourceId: ' existing-1 ', kind: 'duplicate' }, false)
  const repeated = registrationCompletionTransition('restaurant', { status: 'success', resourceId: 'existing-1', kind: 'duplicate' }, true)
  const failed = registrationCompletionTransition('restaurant', { status: 'failure' }, false)
  assert.deepEqual(first, { resourceId: 'existing-1', nextStep: 'creator' })
  assert.equal(repeated, null)
  assert.equal(failed, null)
})
