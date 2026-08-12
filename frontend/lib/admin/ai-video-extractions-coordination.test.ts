import assert from 'node:assert/strict'
import test from 'node:test'

import { nextAiExtractionFilters, reviewActionsFor, reviewRequest } from './ai-video-extractions-coordination.ts'

test('AI 작업 필터를 바꾸면 첫 페이지로 이동한다', () => {
  assert.deepEqual(nextAiExtractionFilters({ executionStatus: '', source: '', reviewStatus: '', page: 3 }, { source: 'ADMIN' }), {
    executionStatus: '', source: 'ADMIN', reviewStatus: '', page: 1,
  })
})

test('자동 확정 결과에는 롤백만 노출한다', () => {
  assert.deepEqual(reviewActionsFor({ executionStatus: 'SUCCEEDED', resultCompleteness: 'COMPLETE', reviewStatus: 'AUTO_CONFIRMED' }), {
    retry: false, confirm: false, discard: false, rollback: true,
  })
})

test('검수 요청에는 현재 검수 상태를 낙관적 잠금 값으로 보낸다', () => {
  assert.deepEqual(reviewRequest('CONFIRM', 'AUTO_BLOCKED'), { decision: 'CONFIRM', expectedReviewStatus: 'AUTO_BLOCKED' })
})
