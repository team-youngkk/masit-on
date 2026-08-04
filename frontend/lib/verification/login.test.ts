import assert from 'node:assert/strict'
import test from 'node:test'

import {
  safeVerificationReturnTo,
  verificationLoginResult,
  verificationReturnToFromHash,
} from './login.ts'

test('안전한 동일 출처 상대 경로는 쿼리와 해시를 유지한다', () => {
  assert.equal(
    safeVerificationReturnTo('/restaurants/1?from=verification#details'),
    '/restaurants/1?from=verification#details',
  )
})

test('외부 URL과 프로토콜 상대 경로는 기본 경로로 대체한다', () => {
  assert.equal(safeVerificationReturnTo('https://example.com/path'), '/')
  assert.equal(safeVerificationReturnTo('//example.com/path'), '/')
  assert.equal(safeVerificationReturnTo('/a/..//example.com'), '/')
  assert.equal(safeVerificationReturnTo('javascript:alert(1)'), '/')
  assert.equal(safeVerificationReturnTo(undefined), '/')
})

test('Nginx fragment는 원래 검색 query의 ampersand까지 복귀 경로로 보존한다', () => {
  const returnTo = verificationReturnToFromHash(
    '#returnTo=/restaurants?region=seoul&category=korean',
  )

  assert.equal(
    safeVerificationReturnTo(returnTo),
    '/restaurants?region=seoul&category=korean',
  )
})

test('204 응답만 로그인 성공으로 처리한다', () => {
  assert.deepEqual(verificationLoginResult(204), { ok: true })
  assert.equal(verificationLoginResult(200).ok, false)
})

test('401은 참여자 등록 여부를 드러내지 않는 일반 메시지를 제공한다', () => {
  assert.deepEqual(verificationLoginResult(401), {
    ok: false,
    message: '로그인 정보를 확인할 수 없습니다. 입력한 정보를 다시 확인해 주세요.',
  })
})

test('429와 503은 재시도 가능 여부를 구분해 안내한다', () => {
  assert.deepEqual(verificationLoginResult(429), {
    ok: false,
    message: '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.',
  })
  assert.deepEqual(verificationLoginResult(503), {
    ok: false,
    message: '현재 검증 참여자 로그인을 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.',
  })
})

test('예상하지 못한 응답은 내부 정보를 노출하지 않는 메시지를 제공한다', () => {
  assert.deepEqual(verificationLoginResult(500), {
    ok: false,
    message: '로그인 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.',
  })
})
