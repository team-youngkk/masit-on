import assert from 'node:assert/strict'
import test from 'node:test'

import { registrationStepDecision } from './registration-progression.ts'

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
