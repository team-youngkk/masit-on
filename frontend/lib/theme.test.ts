import assert from 'node:assert/strict'
import test from 'node:test'

import { isTheme, resolveTheme } from './theme.ts'

test('저장된 테마를 시스템 설정보다 우선한다', () => {
  assert.equal(resolveTheme('dark', false), 'dark')
  assert.equal(resolveTheme('light', true), 'light')
})

test('유효하지 않은 저장값은 시스템 설정으로 대체한다', () => {
  assert.equal(resolveTheme('system', true), 'dark')
  assert.equal(resolveTheme(null, false), 'light')
  assert.equal(resolveTheme(undefined, true), 'dark')
})

test('테마 값은 light 또는 dark만 유효하다', () => {
  assert.equal(isTheme('light'), true)
  assert.equal(isTheme('dark'), true)
  assert.equal(isTheme('system'), false)
  assert.equal(isTheme(null), false)
})
