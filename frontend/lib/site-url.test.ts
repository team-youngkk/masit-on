import assert from 'node:assert/strict'
import test from 'node:test'

import { parseSiteUrl, toPublicSiteUrl } from './site-url.ts'

test('공백을 제거한 HTTPS 사이트 URL을 반환한다', () => {
  const siteUrl = parseSiteUrl('  https://masit-on.example/  ')

  assert.equal(siteUrl?.toString(), 'https://masit-on.example/')
})

test('누락되었거나 URL로 해석할 수 없는 사이트 URL은 거부한다', () => {
  assert.equal(parseSiteUrl(undefined), null)
  assert.equal(parseSiteUrl('   '), null)
  assert.equal(parseSiteUrl('not a url'), null)
})

test('HTTPS가 아니거나 canonical origin으로 쓸 수 없는 사이트 URL은 거부한다', () => {
  assert.equal(parseSiteUrl('http://masit-on.example'), null)
  assert.equal(parseSiteUrl('https://user:pass@masit-on.example'), null)
  assert.equal(parseSiteUrl('https://masit-on.example/production'), null)
  assert.equal(parseSiteUrl('https://masit-on.example/?campaign=seo'), null)
  assert.equal(parseSiteUrl('https://masit-on.example/#restaurants'), null)
})

test('공개 URL은 사이트 내부 절대 경로만 사용한다', () => {
  assert.equal(toPublicSiteUrl('restaurants'), null)
  assert.equal(toPublicSiteUrl('//untrusted.example'), null)
})
