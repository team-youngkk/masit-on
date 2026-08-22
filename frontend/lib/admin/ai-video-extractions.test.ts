import assert from 'node:assert/strict'
import test from 'node:test'

import { clearAccessToken, login } from './auth.ts'
import { AdminApiError } from './api.ts'
import { aiValidationConflictFrom, aiValidationFailureMessageFor, createAiVideoExtraction, registerAiRegistrationUnit, reviewAiVideoExtraction, aiExtractionMessageFor } from './ai-video-extractions.ts'

const job = (reused: boolean) => ({
  jobId: reused ? 'job-reused' : 'job-new',
  source: 'ADMIN',
  youtube: { channelId: 'channel-id', videoId: 'video-id', videoUrl: 'https://www.youtube.com/watch?v=video-id' },
  executionStatus: 'QUEUED',
  resultCompleteness: null,
  reviewStatus: null,
  provider: 'GOOGLE_GEMINI',
  modelVersion: 'gemini-3.5-flash-lite',
  promptVersion: 'P1',
  schemaVersion: 'S1',
  attemptCount: 0,
  createdAt: '2026-08-12T00:00:00Z',
  startedAt: null,
  finishedAt: null,
  reused,
})

test('신규 접수 API는 trim된 입력과 멱등 키를 JSON 본문으로 보내고 reused를 보존한다', async () => {
  const previousFetch = globalThis.fetch
  const requestBodies: unknown[] = []
  const responses = [job(false), job(true)]
  let call = 0
  globalThis.fetch = async (input, init) => {
    if (call++ === 0) return new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresInSeconds: 3600, role: 'ADMIN' }), { status: 200 })
    if (input === '/api/me') return Response.json({ id: 'admin-1', email: 'admin@example.com', role: 'ADMIN' })
    requestBodies.push(JSON.parse(String(init?.body)))
    return new Response(JSON.stringify(responses.shift()), { status: 200 })
  }

  try {
    clearAccessToken()
    await login('admin', 'password')
    const created = await createAiVideoExtraction('  https://youtu.be/video-id  ', '  보완 메모  ', 'key-1')
    const reused = await createAiVideoExtraction('https://youtu.be/video-id', '보완 메모', 'key-1')

    assert.equal(created.reused, false)
    assert.equal(reused.reused, true)
    assert.deepEqual(requestBodies, [
      { videoUrl: 'https://youtu.be/video-id', supplementText: '보완 메모', idempotencyKey: 'key-1' },
      { videoUrl: 'https://youtu.be/video-id', supplementText: '보완 메모', idempotencyKey: 'key-1' },
    ])
  } finally {
    clearAccessToken()
    globalThis.fetch = previousFetch
  }
})

test('빈 보완 텍스트는 신규 접수 요청에서 생략한다', async () => {
  const previousFetch = globalThis.fetch
  const requestBodies: unknown[] = []
  let call = 0
  globalThis.fetch = async (input, init) => {
    if (call++ === 0) return new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresInSeconds: 3600, role: 'ADMIN' }), { status: 200 })
    if (input === '/api/me') return Response.json({ id: 'admin-1', email: 'admin@example.com', role: 'ADMIN' })
    requestBodies.push(JSON.parse(String(init?.body)))
    return new Response(JSON.stringify(job(false)), { status: 202 })
  }

  try {
    clearAccessToken()
    await login('admin', 'password')
    await createAiVideoExtraction('https://youtu.be/video-id', '   ', 'key-2')
    assert.deepEqual(requestBodies, [{ videoUrl: 'https://youtu.be/video-id', idempotencyKey: 'key-2' }])
  } finally {
    clearAccessToken()
    globalThis.fetch = previousFetch
  }
})

test('신규 접수 API 오류는 계약 코드별 안전한 안내로 변환한다', async () => {
  const previousFetch = globalThis.fetch
  let call = 0
  globalThis.fetch = async (input) => {
    if (call++ === 0) return new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresInSeconds: 3600, role: 'ADMIN' }), { status: 200 })
    if (input === '/api/me') return Response.json({ id: 'admin-1', email: 'admin@example.com', role: 'ADMIN' })
    return new Response(JSON.stringify({ code: 'AIEXTRACT_INVALID_VIDEO_URL', message: 'internal detail', traceId: 'trace-1' }), { status: 400 })
  }

  try {
    clearAccessToken()
    await login('admin', 'password')
    let reason: unknown
    try {
      await createAiVideoExtraction('invalid', '', 'key-3')
      assert.fail('잘못된 URL 접수는 실패해야 합니다.')
    } catch (caught) {
      reason = caught
    }
    assert.equal(aiExtractionMessageFor(reason, 'submission'), '공개 YouTube 영상 URL을 확인해 주세요.')
  } finally {
    clearAccessToken()
    globalThis.fetch = previousFetch
  }
})

test('검수 요청은 unitId와 요구한 supplements 키만 본문에 담고 tagDecisions 기본값을 보낸다', async () => {
  const previousFetch = globalThis.fetch
  const requestBodies: unknown[] = []
  let call = 0
  globalThis.fetch = async (input, init) => {
    if (call++ === 0) return new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresInSeconds: 3600, role: 'ADMIN' }), { status: 200 })
    if (input === '/api/me') return Response.json({ id: 'admin-1', email: 'admin@example.com', role: 'ADMIN' })
    requestBodies.push(JSON.parse(String(init?.body)))
    return new Response(null, { status: 204 })
  }

  try {
    clearAccessToken()
    await login('admin', 'password')
    await reviewAiVideoExtraction('job-1', 'CONFIRM', 'unit-1', 'AUTO_BLOCKED', '  Kakao 장소를 확인함  ', {
      supplements: { kakaoPlaceUrl: 'https://place.map.kakao.com/example' },
    })
    assert.deepEqual(requestBodies, [{
      decision: 'CONFIRM',
      unitId: 'unit-1',
      expectedReviewStatus: 'AUTO_BLOCKED',
      reason: 'Kakao 장소를 확인함',
      supplements: { kakaoPlaceUrl: 'https://place.map.kakao.com/example' },
      tagDecisions: [],
    }])
  } finally {
    clearAccessToken()
    globalThis.fetch = previousFetch
  }
})

test('supplements를 지정하지 않은 검수 요청(ROLLBACK 등)은 그 키 자체를 보내지 않는다', async () => {
  const previousFetch = globalThis.fetch
  const requestBodies: unknown[] = []
  let call = 0
  globalThis.fetch = async (input, init) => {
    if (call++ === 0) return new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresInSeconds: 3600, role: 'ADMIN' }), { status: 200 })
    if (input === '/api/me') return Response.json({ id: 'admin-1', email: 'admin@example.com', role: 'ADMIN' })
    requestBodies.push(JSON.parse(String(init?.body)))
    return new Response(null, { status: 204 })
  }

  try {
    clearAccessToken()
    await login('admin', 'password')
    await reviewAiVideoExtraction('job-1', 'ROLLBACK', 'unit-1', 'AUTO_CONFIRMED', '롤백 사유')
    assert.deepEqual(requestBodies, [{
      decision: 'ROLLBACK', unitId: 'unit-1', expectedReviewStatus: 'AUTO_CONFIRMED', reason: '롤백 사유', tagDecisions: [],
    }])
    assert.ok(!('supplements' in (requestBodies[0] as Record<string, unknown>)))
  } finally {
    clearAccessToken()
    globalThis.fetch = previousFetch
  }
})

test('등록 단위 등록 API는 빈 본문으로 요청하고 성공 결과를 그대로 반환한다', async () => {
  const previousFetch = globalThis.fetch
  const requestBodies: Array<string | undefined> = []
  let call = 0
  const result = {
    unitId: 'unit-1', reviewStatus: 'AUTO_CONFIRMED', restaurantId: 'restaurant-1', creatorId: 'creator-1',
    videoId: 'video-1', visitId: 'visit-1', reusedResources: ['creator', 'video'],
    placeDecision: { kakaoPlaceUrl: 'https://place.map.kakao.com/example', roadAddress: '서울특별시 영등포구 도림로131길 17', matchedBy: 'NAME_AND_DISTRICT' },
    categoryDecision: { foodCategoryName: '일식', resolvedBy: 'KAKAO_PLACE_CATEGORY' },
  }
  globalThis.fetch = async (input, init) => {
    if (call++ === 0) return new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresInSeconds: 3600, role: 'ADMIN' }), { status: 200 })
    if (input === '/api/me') return Response.json({ id: 'admin-1', email: 'admin@example.com', role: 'ADMIN' })
    requestBodies.push(init?.body as string | undefined)
    return new Response(JSON.stringify(result), { status: 200 })
  }

  try {
    clearAccessToken()
    await login('admin', 'password')
    const response = await registerAiRegistrationUnit('job-1', 'unit-1')
    assert.deepEqual(response, result)
    assert.deepEqual(requestBodies, [undefined])
  } finally {
    clearAccessToken()
    globalThis.fetch = previousFetch
  }
})

test('등록 단위 등록 API의 422 예외 전환 응답은 blockReason·recoveryPaths·requiredSupplements로 파싱한다', async () => {
  const previousFetch = globalThis.fetch
  let call = 0
  globalThis.fetch = async (input) => {
    if (call++ === 0) return new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresInSeconds: 3600, role: 'ADMIN' }), { status: 200 })
    if (input === '/api/me') return Response.json({ id: 'admin-1', email: 'admin@example.com', role: 'ADMIN' })
    return new Response(JSON.stringify({
      code: 'AIEXTRACT_VALIDATION_CONFLICT',
      details: {
        blockReason: 'PLACE_AMBIGUOUS',
        recoveryPaths: ['SUPPLEMENT', 'MANUAL_REGISTRATION'],
        requiredSupplements: ['kakaoPlaceUrl'],
      },
      traceId: 'trace-2',
    }), { status: 422 })
  }

  try {
    clearAccessToken()
    await login('admin', 'password')
    let reason: unknown
    try {
      await registerAiRegistrationUnit('job-1', 'unit-1')
      assert.fail('예외 전환 응답은 실패해야 합니다.')
    } catch (caught) {
      reason = caught
    }
    assert.ok(reason instanceof AdminApiError)
    assert.deepEqual(aiValidationConflictFrom(reason), {
      blockReason: 'PLACE_AMBIGUOUS',
      recoveryPaths: ['SUPPLEMENT', 'MANUAL_REGISTRATION'],
      requiredSupplements: ['kakaoPlaceUrl'],
      validationFailureReason: null,
      traceId: 'trace-2',
    })
  } finally {
    clearAccessToken()
    globalThis.fetch = previousFetch
  }
})

test('AIEXTRACT_VALIDATION_CONFLICT가 아닌 오류는 예외 전환 정보로 파싱하지 않는다', () => {
  assert.equal(aiValidationConflictFrom(new AdminApiError(409, 'AIEXTRACT_CONCURRENT_REQUEST_CONFLICT')), null)
  assert.equal(aiValidationConflictFrom(new Error('network')), null)
})

test('AdminApiError.details에 이미 언랩된 blockReason·recoveryPaths·requiredSupplements를 정확히 파싱한다', () => {
  const error = new AdminApiError(
    422,
    'AIEXTRACT_VALIDATION_CONFLICT',
    [],
    'trace-3',
    '요청을 처리하지 못했습니다.',
    {
      blockReason: 'MISSING_REQUIRED_FIELD',
      recoveryPaths: ['REEXTRACT', 'MANUAL_REGISTRATION'],
      requiredSupplements: [],
    },
  )

  assert.deepEqual(aiValidationConflictFrom(error), {
    blockReason: 'MISSING_REQUIRED_FIELD',
    recoveryPaths: ['REEXTRACT', 'MANUAL_REGISTRATION'],
    requiredSupplements: [],
    validationFailureReason: null,
    traceId: 'trace-3',
  })
})

test('details가 배열이면 AdminApiError.details는 빈 객체로 정규화된다', async () => {
  const previousFetch = globalThis.fetch
  let call = 0
  globalThis.fetch = async (input) => {
    if (call++ === 0) return new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresInSeconds: 3600, role: 'ADMIN' }), { status: 200 })
    if (input === '/api/me') return Response.json({ id: 'admin-1', email: 'admin@example.com', role: 'ADMIN' })
    return new Response(JSON.stringify({
      code: 'AIEXTRACT_VALIDATION_CONFLICT',
      details: ['PLACE_AMBIGUOUS'],
      traceId: 'trace-4',
    }), { status: 422 })
  }

  try {
    clearAccessToken()
    await login('admin', 'password')
    let reason: unknown
    try {
      await registerAiRegistrationUnit('job-1', 'unit-1')
      assert.fail('예외 전환 응답은 실패해야 합니다.')
    } catch (caught) {
      reason = caught
    }
    assert.ok(reason instanceof AdminApiError)
    assert.deepEqual((reason as AdminApiError).details, {})
    assert.deepEqual(aiValidationConflictFrom(reason), {
      blockReason: null,
      recoveryPaths: [],
      requiredSupplements: [],
      validationFailureReason: null,
      traceId: 'trace-4',
    })
  } finally {
    clearAccessToken()
    globalThis.fetch = previousFetch
  }
})

test('보충 검증 실패 사유는 원래 차단 사유와 실제 복구 경로를 분리해 파싱한다', () => {
  const error = new AdminApiError(
    422,
    'AIEXTRACT_VALIDATION_CONFLICT',
    [],
    'trace-5',
    '요청을 처리하지 못했습니다.',
    {
      blockReason: 'PLACE_AMBIGUOUS',
      recoveryPaths: ['REEXTRACT', 'MANUAL_REGISTRATION'],
      requiredSupplements: [],
      validationFailureReason: 'VISIT_EVIDENCE_REQUIRED',
    },
  )

  assert.deepEqual(aiValidationConflictFrom(error), {
    blockReason: 'PLACE_AMBIGUOUS',
    recoveryPaths: ['REEXTRACT', 'MANUAL_REGISTRATION'],
    requiredSupplements: [],
    validationFailureReason: 'VISIT_EVIDENCE_REQUIRED',
    traceId: 'trace-5',
  })
})

test('보충 후속 중복 실패는 기존 자원 확인 경로만 파싱한다', () => {
  const error = new AdminApiError(
    422,
    'AIEXTRACT_VALIDATION_CONFLICT',
    [],
    undefined,
    undefined,
    {
      blockReason: 'PLACE_AMBIGUOUS',
      recoveryPaths: ['EXISTING_RESOURCE'],
      requiredSupplements: [],
      validationFailureReason: 'DUPLICATE_CONFLICT',
    },
  )

  assert.deepEqual(aiValidationConflictFrom(error), {
    blockReason: 'PLACE_AMBIGUOUS',
    recoveryPaths: ['EXISTING_RESOURCE'],
    requiredSupplements: [],
    validationFailureReason: 'DUPLICATE_CONFLICT',
    traceId: undefined,
  })
})

test('보충 검증 실패 사유가 허용되지 않은 값이면 안전하게 무시한다', () => {
  const error = new AdminApiError(
    422,
    'AIEXTRACT_VALIDATION_CONFLICT',
    [],
    undefined,
    undefined,
    {
      validationFailureReason: 'INTERNAL_ERROR',
    },
  )

  assert.equal(aiValidationConflictFrom(error)?.validationFailureReason, null)
})

test('보충 검증 실패 사유는 화면용 한국어 안내로 변환하고 없는 사유는 숨긴다', () => {
  assert.equal(aiValidationFailureMessageFor('VISIT_EVIDENCE_REQUIRED'), '이번 보충 검증 실패: 방문 근거가 부족합니다.')
  assert.equal(aiValidationFailureMessageFor(null), null)
})
