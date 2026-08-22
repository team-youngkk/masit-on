import assert from 'node:assert/strict'
import test from 'node:test'

import {
  allowedReportTypes,
  createParticipationDetailCoordinator,
  isCurrentParticipationDetailRequest,
  parseParticipationError,
  participationDuplicateRequestId,
  participationErrorMessage,
  participationPayloadKey,
  participationTargetDetails,
  participationTargetSummary,
  updateParticipationListQuery,
} from './participation-coordination.ts'

test('같은 종류와 본문은 재시도에 사용할 같은 요청 지문을 만든다', () => {
  const first = participationPayloadKey('report', { targetId: 'id', candidate: { name: '맛집', address: '서울' } })
  const second = participationPayloadKey('report', { candidate: { address: '서울', name: '맛집' }, targetId: 'id' })
  assert.equal(first, second)
})

test('다음 페이지로 이동하고 종류나 상태 필터가 바뀌면 1페이지로 돌아간다', () => {
  const current = { kind: 'submission', status: '', page: 1 }
  const secondPage = updateParticipationListQuery(current, { page: 2 })
  assert.equal(secondPage.page, 2)
  assert.equal(updateParticipationListQuery(secondPage, { status: 'RECEIVED' }).page, 1)
  assert.equal(updateParticipationListQuery(secondPage, { kind: 'report' }).page, 1)
})

test('중복과 일일 제한 오류는 다음 행동을 안내한다', () => {
  assert.match(participationErrorMessage(409, { code: 'DUPLICATE_OPEN_REPORT' }), /내 요청 목록/)
  assert.match(participationErrorMessage(429, { code: 'DAILY_REQUEST_LIMIT_EXCEEDED' }), /내일/)
})

test('중복 제보·신고 응답에서 기존 요청 식별자를 추출한다', () => {
  assert.equal(participationDuplicateRequestId({
    code: 'DUPLICATE_OPEN_SUBMISSION',
    resource: { requestId: 'submission-1' },
  }), 'submission-1')
  assert.equal(participationDuplicateRequestId({
    code: 'DUPLICATE_OPEN_REPORT',
    resource: { requestId: 'report-1' },
  }), 'report-1')
  assert.equal(participationDuplicateRequestId({
    code: 'DUPLICATE_OPEN_REPORT',
    resource: { requestId: '  ' },
  }), undefined)
  assert.equal(participationDuplicateRequestId({ code: 'DAILY_REQUEST_LIMIT_EXCEEDED' }), undefined)
})

test('탭 전환으로 이전 중복 상세 요청을 무시한다', () => {
  assert.equal(isCurrentParticipationDetailRequest(1, 1, 'submission', 'submission'), true)
  assert.equal(isCurrentParticipationDetailRequest(1, 2, 'submission', 'submission'), false)
  assert.equal(isCurrentParticipationDetailRequest(1, 1, 'submission', 'report'), false)
})

test('지연된 중복 상세 조회가 탭 전환 뒤 selected를 갱신하지 않는다', async () => {
  let resolveDetail!: (detail: { requestId: string }) => void
  const getParticipationDetail = new Promise<{ requestId: string }>(resolve => { resolveDetail = resolve })
  const detailCoordinator = createParticipationDetailCoordinator<string>('submission')
  const selected: { requestId: string }[] = []

  const pending = detailCoordinator.load(
    'submission',
    'submission-1',
    () => getParticipationDetail,
    detail => selected.push(detail),
  )

  detailCoordinator.switchKind('report')
  resolveDetail({ requestId: 'submission-1' })

  assert.equal(await pending, false)
  assert.deepEqual(selected, [])
})

test('대상 유형별로 계약된 신고 유형만 노출한다', () => {
  assert.deepEqual(allowedReportTypes('RESTAURANT'), ['ERROR', 'INAPPROPRIATE_CONTENT', 'CLOSED'])
  assert.deepEqual(allowedReportTypes('VIDEO'), ['ERROR', 'INAPPROPRIATE_CONTENT', 'UNAVAILABLE'])
  assert.deepEqual(allowedReportTypes('VISIT_RELATIONSHIP'), ['ERROR', 'INAPPROPRIATE_CONTENT', 'WRONG_RELATIONSHIP'])
})

test('제보와 신고 대상은 목록 요약과 상세 필드를 구분해 제공한다', () => {
  assert.equal(participationTargetSummary({
    targetType: 'RESTAURANT',
    candidate: { name: '새 맛집', roadAddress: '서울시 테스트로 1' },
  }), '맛집 · 새 맛집 · 서울시 테스트로 1')
  assert.deepEqual(participationTargetDetails({
    targetType: 'VIDEO',
    targetId: 'video-id',
    reportType: 'UNAVAILABLE',
  }), [['대상 식별자', 'video-id'], ['신고 유형', '이용 불가']])
})

test('Response 오류는 traceId를 포함한 계약 본문을 함께 반환한다', async () => {
  const response = new Response(
    JSON.stringify({ code: 'PARTICIPATION_TARGET_NOT_FOUND', message: '대상을 찾을 수 없습니다.', traceId: 'trace-1' }),
    { status: 404 },
  )
  const parsed = await parseParticipationError(response)
  assert.deepEqual(parsed, {
    status: 404,
    contract: { code: 'PARTICIPATION_TARGET_NOT_FOUND', message: '대상을 찾을 수 없습니다.', traceId: 'trace-1' },
  })
})

test('JSON이 아닌 오류 본문은 상태 코드만 담고 빈 계약으로 대체한다', async () => {
  const response = new Response('not-json', { status: 502 })
  const parsed = await parseParticipationError(response)
  assert.deepEqual(parsed, { status: 502, contract: {} })
})

test('Response가 아닌 원인은 네트워크 오류로 간주해 null을 반환한다', async () => {
  assert.equal(await parseParticipationError(new TypeError('network down')), null)
})
