import assert from 'node:assert/strict'
import test from 'node:test'

import { aiConfidenceTone } from './ai-confidence.ts'

test('AI confidence UI 정책은 0.6과 0.8 경계에서 톤을 전환한다', () => {
  assert.equal(aiConfidenceTone(0), 'danger')
  assert.equal(aiConfidenceTone(0.5999), 'danger')
  assert.equal(aiConfidenceTone(0.6), 'warning')
  assert.equal(aiConfidenceTone(0.7999), 'warning')
  assert.equal(aiConfidenceTone(0.8), 'success')
  assert.equal(aiConfidenceTone(1), 'success')
})
