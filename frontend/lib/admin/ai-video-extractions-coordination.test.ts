import assert from 'node:assert/strict'
import test from 'node:test'

import {
  aiExtractionMessageForCode,
  aiExtractionSubmissionFieldErrors,
  aiExtractionSubmissionAttempt,
  aiExtractionSubmissionPresentation,
  candidateTruncatedBannerMessage,
  exceptionActionsFor,
  nextAiExtractionFilters,
  registrationUnitActionsFor,
  retryActionAvailable,
  reviewRequest,
} from './ai-video-extractions-coordination.ts'

test('신규 접수 입력 오류는 각 필드에 연결할 수 있는 형태로 반환한다', () => {
  const expected = {
    videoUrl: 'YouTube 영상 URL을 입력해 주세요.',
    supplementText: '보완 텍스트는 앞뒤 공백을 제외하고 20,000자 이하로 입력해 주세요.',
  }
  assert.deepEqual(aiExtractionSubmissionFieldErrors('', 'a'.repeat(20_001)), expected)
  assert.deepEqual(aiExtractionSubmissionFieldErrors('   ', 'a'.repeat(20_001)), expected)
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

test('재시도는 실패한 작업과 부분 완료 작업에서만 가능하다', () => {
  assert.equal(retryActionAvailable({ executionStatus: 'FAILED', resultCompleteness: null }, []), true)
  assert.equal(retryActionAvailable({ executionStatus: 'SUCCEEDED', resultCompleteness: 'PARTIAL' }, []), true)
  assert.equal(retryActionAvailable({ executionStatus: 'SUCCEEDED', resultCompleteness: 'COMPLETE' }, []), false)
  assert.equal(retryActionAvailable({ executionStatus: 'RUNNING', resultCompleteness: null }, []), false)
})

test('SUCCEEDED/COMPLETE 작업도 등록 단위 중 하나가 AUTO_BLOCKED이면 재시도할 수 있다', () => {
  assert.equal(
    retryActionAvailable(
      { executionStatus: 'SUCCEEDED', resultCompleteness: 'COMPLETE' },
      [{ reviewStatus: 'AUTO_CONFIRMED' }, { reviewStatus: 'AUTO_BLOCKED' }],
    ),
    true,
  )
})

test('SUCCEEDED/COMPLETE 작업의 등록 단위가 전부 AUTO_CONFIRMED이면 재시도할 수 없다', () => {
  assert.equal(
    retryActionAvailable(
      { executionStatus: 'SUCCEEDED', resultCompleteness: 'COMPLETE' },
      [{ reviewStatus: 'AUTO_CONFIRMED' }, { reviewStatus: 'AUTO_CONFIRMED' }],
    ),
    false,
  )
})

test('차단 등록 단위는 등록 실행만 노출한다', () => {
  assert.deepEqual(registrationUnitActionsFor({ reviewStatus: 'AUTO_BLOCKED', manualOverrideType: null }), {
    registerable: true, adjustCategory: false, rollback: false,
  })
})

test('거부(AUTO_REJECTED)는 등록 단위 일괄 등록 API가 항상 거절하는 종결 상태라 어떤 조치도 노출하지 않는다', () => {
  assert.deepEqual(registrationUnitActionsFor({ reviewStatus: 'AUTO_REJECTED', manualOverrideType: null }), {
    registerable: false, adjustCategory: false, rollback: false,
  })
})

test('자동 확정 등록 단위는 카테고리 보정과 롤백을 노출한다', () => {
  assert.deepEqual(registrationUnitActionsFor({ reviewStatus: 'AUTO_CONFIRMED', manualOverrideType: null }), {
    registerable: false, adjustCategory: true, rollback: true,
  })
})

test('등록이 유지된 MANUAL_OVERRIDE(사후 보정·카테고리 보정)는 카테고리 보정과 롤백을 노출한다', () => {
  assert.deepEqual(registrationUnitActionsFor({ reviewStatus: 'MANUAL_OVERRIDE', manualOverrideType: null }), {
    registerable: false, adjustCategory: true, rollback: true,
  })
})

test('롤백 완료·폐기 완료 MANUAL_OVERRIDE는 어떤 조치도 노출하지 않는다', () => {
  assert.deepEqual(registrationUnitActionsFor({ reviewStatus: 'MANUAL_OVERRIDE', manualOverrideType: 'ROLLED_BACK' }), {
    registerable: false, adjustCategory: false, rollback: false,
  })
  assert.deepEqual(registrationUnitActionsFor({ reviewStatus: 'MANUAL_OVERRIDE', manualOverrideType: 'DISCARDED' }), {
    registerable: false, adjustCategory: false, rollback: false,
  })
})

test('예외 화면 조치는 recoveryPaths 배열 내용을 그대로 반영하고 순서를 보존한다', () => {
  assert.deepEqual(exceptionActionsFor('AUTO_BLOCKED', ['SUPPLEMENT', 'MANUAL_REGISTRATION']), ['SUPPLEMENT', 'MANUAL_REGISTRATION', 'DISCARD'])
  assert.deepEqual(exceptionActionsFor('AUTO_BLOCKED', ['EXISTING_RESOURCE']), ['EXISTING_RESOURCE', 'DISCARD'])
})

test('빈 recoveryPaths도 AUTO_BLOCKED 단위이면 DISCARD만 노출한다', () => {
  assert.deepEqual(exceptionActionsFor('AUTO_BLOCKED', []), ['DISCARD'])
})

test('AUTO_BLOCKED가 아닌 거절(AUTO_REJECTED 등)은 DISCARD를 추가하지 않는다', () => {
  assert.deepEqual(exceptionActionsFor('AUTO_REJECTED', []), [])
  assert.deepEqual(exceptionActionsFor('MANUAL_OVERRIDE', []), [])
})

test('후보 절삭 배너는 candidateTruncated가 true일 때만 고정 문구를 반환한다', () => {
  assert.equal(candidateTruncatedBannerMessage(true), '후보 수 상한(300)을 넘어 일부 장소가 누락됐습니다.')
  assert.equal(candidateTruncatedBannerMessage(false), null)
})

test('검수 요청은 등록 단위 ID와 현재 검수 상태를 낙관적 잠금 값으로 담는다', () => {
  assert.deepEqual(reviewRequest('CONFIRM', 'unit-1', 'AUTO_BLOCKED', { kakaoPlaceUrl: 'https://place.map.kakao.com/example' }), {
    decision: 'CONFIRM', unitId: 'unit-1', expectedReviewStatus: 'AUTO_BLOCKED', supplements: { kakaoPlaceUrl: 'https://place.map.kakao.com/example' },
  })
  assert.deepEqual(reviewRequest('ROLLBACK', 'unit-1', 'AUTO_CONFIRMED'), {
    decision: 'ROLLBACK', unitId: 'unit-1', expectedReviewStatus: 'AUTO_CONFIRMED',
  })
})

test('AI 오류 코드는 상태 코드가 아닌 계약 코드별 안내로 분기한다', () => {
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_RETRY_BLOCKED'), '현재 작업 상태에서는 재시도할 수 없습니다.')
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_DUPLICATE_CONFLICT'), '다른 검수 변경과 충돌했습니다. 최신 작업 상태를 다시 조회한 뒤 진행해 주세요.')
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_VALIDATION_CONFLICT'), '후보 검증에 실패해 등록 또는 검수를 완료하지 못했습니다. 최신 작업 상태를 확인해 주세요.')
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_UNIT_NOT_FOUND'), '등록 단위를 찾을 수 없습니다. 최신 작업 상태를 다시 조회해 주세요.')
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_UNIT_ID_REQUIRED'), '등록 단위가 여러 개인 작업입니다. 처리할 등록 단위를 다시 선택해 주세요.')
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_CONCURRENT_REQUEST_CONFLICT'), '같은 등록 단위에 대한 다른 요청과 충돌했습니다. 최신 상태를 다시 조회한 뒤 진행해 주세요.')
})

test('신규 접수 오류는 URL 오류와 정식 등록 충돌을 구분해 안내한다', () => {
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_INVALID_VIDEO_URL'), '공개 YouTube 영상 URL을 확인해 주세요.')
  assert.equal(aiExtractionMessageForCode('AIEXTRACT_DUPLICATE_CONFLICT', 'submission'), '이미 정식 등록된 영상과 충돌해 작업을 접수하지 못했습니다.')
})
