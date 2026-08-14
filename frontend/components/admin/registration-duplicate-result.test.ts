import assert from 'node:assert/strict'
import test from 'node:test'

import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

import { RegistrationDuplicateResult } from './RegistrationDuplicateResult.ts'

test('중복 자원 안내와 기존 자원 확인 버튼을 함께 렌더링한다', () => {
  const html = renderToStaticMarkup(createElement(RegistrationDuplicateResult, {
    resourceName: '맛집',
    existing: 'id: restaurant-1\nname: 기존 맛집',
    onContinue: () => undefined,
  }))

  assert.match(html, /이미 등록된 맛집입니다/)
  assert.match(html, /id: restaurant-1/)
  assert.match(html, /기존 맛집 사용하고 다음 단계/)
  assert.match(html, /type="button"/)
})

test('다음 단계 콜백이 없는 단독 등록 화면은 중복 안내만 렌더링한다', () => {
  const html = renderToStaticMarkup(createElement(RegistrationDuplicateResult, {
    resourceName: '맛집',
    existing: null,
  }))

  assert.match(html, /이미 등록된 맛집입니다/)
  assert.doesNotMatch(html, /다음 단계/)
})
