const assert = require('node:assert/strict')
const test = require('node:test')
const {
  memberLoginHref,
  memberSignupHref,
  memberVerifyEmailHref,
  safeMemberReturnTo,
} = require('./auth-navigation.ts')

test('로그인 링크는 기본 로그인 화면으로 이동한다', () => {
  assert.equal(memberLoginHref(undefined), '/login')
})

test('로그인 화면의 회원가입 링크는 기본 회원가입 화면으로 이동한다', () => {
  assert.equal(memberSignupHref(undefined), '/signup')
})

test('이메일 인증 링크는 기본 인증 화면으로 이동한다', () => {
  assert.equal(memberVerifyEmailHref(undefined), '/verify-email')
})

test('로그인 링크는 안전한 내부 복귀 경로를 유지한다', () => {
  assert.equal(
    memberLoginHref('/restaurants/1?from=favorite#details'),
    '/login?returnTo=%2Frestaurants%2F1%3Ffrom%3Dfavorite%23details',
  )
})

test('로그인 화면의 회원가입 링크는 안전한 내부 복귀 경로를 유지한다', () => {
  assert.equal(
    memberSignupHref('/restaurants/1?from=favorite#details'),
    '/signup?returnTo=%2Frestaurants%2F1%3Ffrom%3Dfavorite%23details',
  )
})

test('이메일 인증 링크는 안전한 내부 복귀 경로를 유지한다', () => {
  assert.equal(
    memberVerifyEmailHref('/restaurants/1?from=favorite#details'),
    '/verify-email?returnTo=%2Frestaurants%2F1%3Ffrom%3Dfavorite%23details',
  )
})

test('외부 또는 프로토콜 상대 경로는 회원가입 링크에 전달하지 않는다', () => {
  assert.equal(memberLoginHref('https://example.com'), '/login')
  assert.equal(memberVerifyEmailHref('https://example.com'), '/verify-email')
  assert.equal(memberSignupHref('https://example.com'), '/signup')
  assert.equal(memberLoginHref('//example.com/path'), '/login')
  assert.equal(memberVerifyEmailHref('//example.com/path'), '/verify-email')
  assert.equal(memberSignupHref('//example.com/path'), '/signup')
  assert.equal(memberLoginHref('/a/..//example.com'), '/login')
  assert.equal(memberVerifyEmailHref('/a/..//example.com'), '/verify-email')
  assert.equal(memberSignupHref('/a/..//example.com'), '/signup')
  assert.equal(memberLoginHref('/.//example.com'), '/login')
  assert.equal(memberVerifyEmailHref('/.//example.com'), '/verify-email')
  assert.equal(memberSignupHref('/.//example.com'), '/signup')
  assert.equal(safeMemberReturnTo('javascript:alert(1)'), null)
})
