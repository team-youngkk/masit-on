import assert from 'node:assert/strict'
import test from 'node:test'

import {
  classifyCourseRouteError,
  courseFailureCategoryLabel,
  courseInvalidCategoryLabel,
  didCourseSelectionChange,
  formatCourseDistance,
  formatCourseDuration,
  isCourseRouteExpired,
  msUntilCourseRouteExpiry,
} from './course-screen-state.ts'
import { addCourseCandidate } from './course-selection.ts'

test('선택 개수·중복·존재하지 않음·비공개·좌표·거리 오류 코드는 invalid로 분류한다', () => {
  const codes = [
    'INVALID_COURSE_SIZE',
    'DUPLICATE_RESTAURANT_IN_COURSE',
    'INVALID_IDENTIFIER',
    'INVALID_REQUEST',
    'RESTAURANT_NOT_FOUND',
    'RESTAURANT_NOT_PUBLIC',
    'RESTAURANT_COORDINATE_REQUIRED',
    'COURSE_DISTANCE_LIMIT_EXCEEDED',
  ] as const

  for (const code of codes) {
    const outcome = classifyCourseRouteError({
      code,
      message: '오류',
      traceId: 'trace-1',
    })
    assert.equal(outcome.kind, 'invalid')
    if (outcome.kind === 'invalid') {
      assert.equal(outcome.category, code)
      assert.equal(outcome.message, '오류')
      assert.equal(outcome.traceId, 'trace-1')
    }
  }
})

test('failureCategory가 PARTIAL·PROVIDER_UNAVAILABLE·SERVICE_RATE_LIMIT이면 failure로 분류한다', () => {
  for (const failureCategory of ['PARTIAL', 'PROVIDER_UNAVAILABLE', 'SERVICE_RATE_LIMIT'] as const) {
    const outcome = classifyCourseRouteError({
      code: 'COURSE_ROUTE_PARTIAL_FAILURE',
      message: '일부 구간의 경로 계산에 실패했습니다.',
      traceId: 'trace-2',
      details: { failureCategory },
    })
    assert.equal(outcome.kind, 'failure')
    if (outcome.kind === 'failure') {
      assert.equal(outcome.category, failureCategory)
      assert.equal(outcome.traceId, 'trace-2')
    }
  }
})

test('retryGuidance.action에 RETRY가 포함되면 재시도를 허용한다', () => {
  const outcome = classifyCourseRouteError({
    code: 'COURSE_ROUTE_PARTIAL_FAILURE',
    message: '일부 구간의 경로 계산에 실패했습니다.',
    details: {
      failureCategory: 'PARTIAL',
      retryGuidance: { action: 'RESELECT_OR_RETRY' },
    },
  })
  assert.equal(outcome.kind, 'failure')
  if (outcome.kind === 'failure') {
    assert.equal(outcome.retryAllowed, true)
  }
})

test('서버가 재시도 불가 action을 내려줄 경우를 대비해 RETRY가 없으면 재시도를 비활성화한다', () => {
  const outcome = classifyCourseRouteError({
    code: 'COURSE_ROUTE_PROVIDER_UNAVAILABLE',
    message: '외부 경로 제공자를 사용할 수 없습니다.',
    details: {
      failureCategory: 'PROVIDER_UNAVAILABLE',
      retryGuidance: { action: 'RESELECT_ONLY' },
    },
  })
  assert.equal(outcome.kind, 'failure')
  if (outcome.kind === 'failure') {
    assert.equal(outcome.retryAllowed, false)
  }

  const withoutGuidance = classifyCourseRouteError({
    code: 'COURSE_ROUTE_PROVIDER_UNAVAILABLE',
    message: '외부 경로 제공자를 사용할 수 없습니다.',
    details: { failureCategory: 'PROVIDER_UNAVAILABLE' },
  })
  assert.equal(withoutGuidance.kind, 'failure')
  if (withoutGuidance.kind === 'failure') {
    assert.equal(withoutGuidance.retryAllowed, false)
  }
})

test('알려지지 않은 코드·failureCategory는 일반 오류로 분류한다', () => {
  assert.deepEqual(
    classifyCourseRouteError({ code: 'INTERNAL_SERVER_ERROR', message: '실패', traceId: 't' }),
    { kind: 'error', message: '실패', traceId: 't' },
  )
  assert.deepEqual(classifyCourseRouteError(null), {
    kind: 'error',
    message: '코스 경로를 계산하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    traceId: undefined,
  })
})

test('invalid·failure 범주 라벨은 색상 없이 구분되는 한글 문구를 반환한다', () => {
  assert.equal(courseInvalidCategoryLabel('RESTAURANT_COORDINATE_REQUIRED'), '좌표 없음')
  assert.equal(courseInvalidCategoryLabel('COURSE_DISTANCE_LIMIT_EXCEEDED'), '30km 초과')
  assert.equal(courseFailureCategoryLabel('PARTIAL'), '부분 실패')
  assert.equal(courseFailureCategoryLabel('PROVIDER_UNAVAILABLE'), '외부 장애')
})

test('expiresAt이 현재 시각 이전이거나 같으면 만료로 판정한다', () => {
  assert.equal(isCourseRouteExpired('2026-08-10T12:05:00+09:00', Date.parse('2026-08-10T12:04:59+09:00')), false)
  assert.equal(isCourseRouteExpired('2026-08-10T12:05:00+09:00', Date.parse('2026-08-10T12:05:00+09:00')), true)
  assert.equal(isCourseRouteExpired('2026-08-10T12:05:00+09:00', Date.parse('2026-08-10T12:05:01+09:00')), true)
})

test('expiresAt을 해석할 수 없으면 만료로 취급해 오래된 결과를 최신처럼 보이지 않게 한다', () => {
  assert.equal(isCourseRouteExpired('not-a-date', Date.now()), true)
})

test('거리는 소수 첫째 자리 km, 시간은 반올림한 분으로 표시한다', () => {
  assert.equal(formatCourseDistance(4200), '4.2km')
  assert.equal(formatCourseDuration(780), '약 13분')
  assert.equal(formatCourseDuration(750), '약 13분')
})

test('expiresAt까지 남은 시간이 있으면 그 밀리초를 반환한다', () => {
  const now = Date.parse('2026-08-10T12:00:00+09:00')
  assert.equal(msUntilCourseRouteExpiry('2026-08-10T12:00:05+09:00', now), 5_000)
})

test('expiresAt이 이미 지났거나 해석할 수 없으면 null을 반환해 타이머를 걸지 않는다', () => {
  const now = Date.parse('2026-08-10T12:00:00+09:00')
  assert.equal(msUntilCourseRouteExpiry('2026-08-10T11:59:59+09:00', now), null)
  assert.equal(msUntilCourseRouteExpiry('2026-08-10T12:00:00+09:00', now), null)
  assert.equal(msUntilCourseRouteExpiry('not-a-date', now), null)
})

test('만료 직전·동일·직후 경계에서 msUntilCourseRouteExpiry가 일관되게 동작한다', () => {
  const expiresAt = '2026-08-10T12:05:00+09:00'
  assert.equal(msUntilCourseRouteExpiry(expiresAt, Date.parse('2026-08-10T12:04:59+09:00')), 1_000)
  assert.equal(msUntilCourseRouteExpiry(expiresAt, Date.parse('2026-08-10T12:05:00+09:00')), null)
  assert.equal(msUntilCourseRouteExpiry(expiresAt, Date.parse('2026-08-10T12:05:01+09:00')), null)
})

test('선택 목록의 구성·순서가 그대로면 변경으로 보지 않는다(정상 케이스)', () => {
  const a = { id: 'a' }
  const b = { id: 'b' }
  assert.equal(didCourseSelectionChange([a, b], [a, b]), false)
  assert.equal(didCourseSelectionChange([a, b], [{ id: 'a' }, { id: 'b' }]), false)
})

test('이미 선택됐거나 상한 초과로 addCourseCandidate가 같은 목록을 반환하면 변경으로 보지 않는다(경쟁 상태 방지)', () => {
  // 계산 응답을 기다리는 동안 추가가 목록을 바꾸지 못했다면(이미 선택됨·5개 상한)
  // 진행 중인 요청과 표시 중인 결과를 무효화하지 않아야 한다.
  const alreadySelected = [
    { id: 'a', name: 'A', district: '강남구', category: '한식' },
    { id: 'b', name: 'B', district: '서초구', category: '중식' },
  ]
  const afterReAdd = addCourseCandidate(alreadySelected, alreadySelected[0])
  assert.equal(
    didCourseSelectionChange(alreadySelected, afterReAdd),
    false,
    '이미 선택된 항목을 다시 추가해도 변경으로 보지 않는다',
  )

  const full = [
    { id: 'a', name: 'A', district: '강남구', category: '한식' },
    { id: 'b', name: 'B', district: '서초구', category: '중식' },
    { id: 'c', name: 'C', district: '마포구', category: '일식' },
    { id: 'd', name: 'D', district: '종로구', category: '양식' },
    { id: 'e', name: 'E', district: '용산구', category: '분식' },
  ]
  const afterOverflowAdd = addCourseCandidate(full, {
    id: 'f',
    name: 'F',
    district: '성동구',
    category: '카페',
  })
  assert.equal(
    didCourseSelectionChange(full, afterOverflowAdd),
    false,
    '5개 상한을 넘겨 추가해도 변경으로 보지 않는다',
  )
})

test('선택 목록의 구성이나 순서가 바뀌면 변경으로 판정해 진행 중 요청을 무효화하게 한다', () => {
  const a = { id: 'a' }
  const b = { id: 'b' }
  const c = { id: 'c' }
  assert.equal(didCourseSelectionChange([a, b], [a, b, c]), true, '추가로 개수가 늘면 변경')
  assert.equal(didCourseSelectionChange([a, b, c], [a, b]), true, '삭제로 개수가 줄면 변경')
  assert.equal(didCourseSelectionChange([a, b], [b, a]), true, '순서만 바뀌어도 변경')
})
