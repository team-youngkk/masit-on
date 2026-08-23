import assert from 'node:assert/strict'
import test from 'node:test'

import { memberLoginDestination } from './auth-navigation.ts'
import { getLoginPageAction, shouldPreserveLoginForm } from './login-page-navigation.ts'

test('로그인 상태 확인 중에는 로그인 화면을 보류한다', () => {
  assert.equal(getLoginPageAction('loading'), 'wait')
})

test('이미 로그인한 사용자는 로그인 화면 대신 기본 화면으로 이동한다', () => {
  assert.equal(getLoginPageAction('authenticated'), 'redirect')
})

test('비로그인 상태에서만 로그인 화면을 표시한다', () => {
  assert.equal(getLoginPageAction('anonymous'), 'render')
})

test('인증 상태를 확인할 수 없으면 재시도 화면을 표시한다', () => {
  assert.equal(getLoginPageAction('unavailable'), 'retry')
})

test('이미 표시한 로그인 폼은 세션 확인 중에도 유지한다', () => {
  assert.equal(shouldPreserveLoginForm('wait', false), false)
  assert.equal(shouldPreserveLoginForm('wait', true), true)
  assert.equal(shouldPreserveLoginForm('retry', false), true)
  assert.equal(shouldPreserveLoginForm('render', false), true)
  assert.equal(shouldPreserveLoginForm('redirect', true), false)
})

test('로그인 완료 목적지는 안전한 복귀 경로를 유지하고 인증 경로는 기본 화면으로 대체한다', () => {
  assert.equal(memberLoginDestination(undefined), '/restaurants')
  assert.equal(memberLoginDestination('/admin'), '/admin')
  assert.equal(memberLoginDestination('/restaurants/1?from=login'), '/restaurants/1?from=login')
  assert.equal(memberLoginDestination('/login'), '/restaurants')
  assert.equal(memberLoginDestination('/admin\\visits\\new'), '/restaurants')
  assert.equal(memberLoginDestination('/admin%252Fvisits%252Fnew'), '/restaurants')
  assert.equal(memberLoginDestination('https://example.com'), '/restaurants')
})
