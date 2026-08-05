import assert from 'node:assert/strict'
import test from 'node:test'

import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

import { CollectionScreenState } from './CollectionScreenState.ts'

function render(state: Parameters<typeof CollectionScreenState>[0]) {
  return renderToStaticMarkup(createElement(CollectionScreenState, state))
}

test('정상 상태는 컬렉션 내용을 표시한다', () => {
  const html = render({
    state: 'normal',
    children: createElement('ul', null, createElement('li', null, '가족과 갈 곳')),
  })

  assert.match(html, /가족과 갈 곳/)
  assert.doesNotMatch(html, /data-collection-state/)
})

test('빈 상태는 오류가 아닌 안내 상태로 표시한다', () => {
  const html = render({ state: 'empty', message: '아직 만든 컬렉션이 없습니다.' })

  assert.match(html, /data-collection-state="empty"/)
  assert.doesNotMatch(html, /role="alert"/)
})

test('인증 만료 상태는 재로그인 후 복귀 링크를 표시한다', () => {
  const html = render({
    state: 'authentication',
    message: '로그인이 필요합니다.',
    action: createElement('a', { href: '/login?returnTo=%2Fme%2Fcollections' }, '로그인하기'),
  })

  assert.match(html, /role="alert"/)
  assert.match(html, /returnTo=%2Fme%2Fcollections/)
})

test('타 회원 컬렉션 404는 내용을 노출하지 않는 상태로 표시한다', () => {
  const html = render({ state: 'not-found', message: '컬렉션을 찾을 수 없습니다.' })

  assert.match(html, /data-collection-state="not-found"/)
  assert.doesNotMatch(html, /restaurantId|collectionId/)
})

test('API 오류는 traceId와 재시도 제어를 함께 표시한다', () => {
  const html = render({
    state: 'error',
    message: '컬렉션 목록을 불러오지 못했습니다.',
    traceId: 'trace-collection-load',
    action: createElement('button', { type: 'button' }, '다시 시도'),
  })

  assert.match(html, /traceId: trace-collection-load/)
  assert.match(html, /<button type="button">다시 시도<\/button>/)
})
