import assert from 'node:assert/strict'
import test from 'node:test'

import { aiConfidencePresentation, aiConfidenceTone } from './ai-confidence.ts'

test('AI confidence UI 정책은 0.6과 0.8 경계에서 톤을 전환한다', () => {
  assert.equal(aiConfidenceTone(0), 'danger')
  assert.equal(aiConfidenceTone(0.5999), 'danger')
  assert.equal(aiConfidenceTone(0.6), 'warning')
  assert.equal(aiConfidenceTone(0.7999), 'warning')
  assert.equal(aiConfidenceTone(0.8), 'success')
  assert.equal(aiConfidenceTone(1), 'success')
})

test('AI confidence 라벨과 퍼센트는 톤과 같은 임계값을 사용한다', () => {
  assert.deepEqual(aiConfidencePresentation(0.5999), { label: '낮음', percentage: 60 })
  assert.deepEqual(aiConfidencePresentation(0.6), { label: '중간', percentage: 60 })
  assert.deepEqual(aiConfidencePresentation(0.8), { label: '높음', percentage: 80 })
  assert.deepEqual(aiConfidencePresentation(Number.NaN), { label: '확인 필요', percentage: 0 })
})
