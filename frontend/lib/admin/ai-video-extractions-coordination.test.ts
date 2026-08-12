import assert from 'node:assert/strict'
import test from 'node:test'

import { aiExtractionMessageForCode, nextAiExtractionFilters, reviewActionsFor, reviewRequest } from './ai-video-extractions-coordination.ts'

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

test('AI 오류 코드는 상태 코드가 아닌 계약 코드별 안내로 분기한다', () => {
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_RETRY_BLOCKED'), '현재 작업 상태에서는 재시도할 수 없습니다.')
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_DUPLICATE_CONFLICT'), '다른 검수 변경과 충돌했습니다. 최신 작업 상태를 다시 조회한 뒤 진행해 주세요.')
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_VALIDATION_CONFLICT'), '후보 검증에 실패해 등록 또는 검수를 완료하지 못했습니다. 최신 작업 상태를 확인해 주세요.')
})
