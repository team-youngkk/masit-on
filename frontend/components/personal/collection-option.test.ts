import assert from 'node:assert/strict'
import test from 'node:test'

import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

import { CollectionOption } from './CollectionOption.ts'

function render(additionStatus: 'AVAILABLE' | 'ALREADY_INCLUDED' | 'LIMIT_REACHED') {
  return renderToStaticMarkup(createElement(CollectionOption, {
    option: {
      collectionId: `collection-${additionStatus}`,
      name: '가족과 갈 곳',
      restaurantCount: 3,
      additionStatus,
    },
  }))
}

test('추가 가능한 옵션은 이름과 공개·활성 맛집 수와 상태를 표시한다', () => {
  const html = render('AVAILABLE')

  assert.match(html, /가족과 갈 곳 · 공개·활성 맛집 3곳 · 추가 가능/)
  assert.doesNotMatch(html, /disabled/)
})

test('이미 포함된 옵션은 상태를 표시하고 비활성화한다', () => {
  const html = render('ALREADY_INCLUDED')

  assert.match(html, /이미 담김/)
  assert.match(html, /disabled=""/)
})

test('상한에 도달한 옵션은 상태를 표시하고 비활성화한다', () => {
  const html = render('LIMIT_REACHED')

  assert.match(html, /100곳 상한 도달/)
  assert.match(html, /disabled=""/)
})
