import assert from 'node:assert/strict'
import test from 'node:test'

import {
  aiExtractionMessageForCode,
  aiExtractionSubmissionFieldErrors,
  aiExtractionSubmissionAttempt,
  aiExtractionSubmissionPresentation,
  nextAiExtractionFilters,
  reviewActionsFor,
  reviewRequest,
  validateAiExtractionSubmission,
} from './ai-video-extractions-coordination.ts'

test('신규 접수는 빈 URL과 trim 후 보완 텍스트 길이를 클라이언트에서 검증한다', () => {
  assert.deepEqual(validateAiExtractionSubmission('', ''), ['YouTube 영상 URL을 입력해 주세요.'])
  assert.deepEqual(validateAiExtractionSubmission('   ', ''), ['YouTube 영상 URL을 입력해 주세요.'])
  assert.deepEqual(validateAiExtractionSubmission('   ', 'a'.repeat(20_001)), [
    'YouTube 영상 URL을 입력해 주세요.',
    '보완 텍스트는 공백을 제외하고 20,000자 이하로 입력해 주세요.',
  ])
  assert.deepEqual(validateAiExtractionSubmission('https://youtu.be/video-id', ` ${'a'.repeat(20_000)} `), [])
})

test('신규 접수 입력 오류는 각 필드에 연결할 수 있는 형태로 반환한다', () => {
  assert.deepEqual(aiExtractionSubmissionFieldErrors('   ', 'a'.repeat(20_001)), {
    videoUrl: 'YouTube 영상 URL을 입력해 주세요.',
    supplementText: '보완 텍스트는 공백을 제외하고 20,000자 이하로 입력해 주세요.',
  })
  assert.deepEqual(aiExtractionSubmissionFieldErrors('https://youtu.be/video-id', ''), {})
})

test('재사용 접수 결과는 기존 작업 ID와 상태를 표시하고 기존 작업 링크를 사용한다', () => {
  assert.deepEqual(aiExtractionSubmissionPresentation({ jobId: 'job-1', executionStatus: 'RUNNING', reused: true }), {
    linkLabel: '기존 작업 보기',
    statusLabel: '기존 작업 ID job-1 · 현재 상태 RUNNING',
  })
  assert.deepEqual(aiExtractionSubmissionPresentation({ jobId: 'job-2', executionStatus: 'QUEUED', reused: false }), {
    linkLabel: '작업 상세 보기',
    statusLabel: undefined,
  })
})

test('동일한 정규화 입력은 멱등 키를 재사용하고 입력이 바뀌면 새 키를 만든다', () => {
  let sequence = 0
  const generate = () => `key-${++sequence}`
  const first = aiExtractionSubmissionAttempt(null, 'url\n메모', generate)
  const retry = aiExtractionSubmissionAttempt(first, 'url\n메모', generate)
  const changed = aiExtractionSubmissionAttempt(retry, 'url\n다른 메모', generate)
  assert.equal(first.key, 'key-1')
  assert.equal(retry.key, 'key-1')
  assert.equal(changed.key, 'key-2')
})

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

test('신규 접수 오류는 URL 오류와 정식 등록 충돌을 구분해 안내한다', () => {
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_INVALID_VIDEO_URL'), '공개 YouTube 영상 URL을 확인해 주세요.')
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_DUPLICATE_CONFLICT', 'submission'), '이미 정식 등록된 영상과 충돌해 작업을 접수하지 못했습니다.')
})
