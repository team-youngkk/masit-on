import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildRestaurantFilterClearHref,
  buildRestaurantFiltersResetHref,
  type RestaurantStructuredFilterKey,
} from './restaurants-filter-navigation.ts'

const FILTER_VALUES = {
  query: '성수 맛집',
  district: '성동구',
  category: '한식',
  creatorId: 'opaque-creator-id',
} as const

test('선택한 구조화 필터만 제거하고 나머지 조건과 size를 유지하며 첫 페이지로 이동한다', () => {
  const keys = Object.keys(FILTER_VALUES) as RestaurantStructuredFilterKey[]

  for (const key of keys) {
    const current = new URLSearchParams({
      ...FILTER_VALUES,
      page: '7',
      size: '50',
      tag: 'MENU_NAENGMYEON',
      unknown: 'discarded',
    })
    const href = buildRestaurantFilterClearHref(current, key)
    const next = new URL(href, 'https://example.com')

    assert.equal(next.pathname, '/restaurants')
    assert.equal(next.searchParams.get(key), null)
    for (const preservedKey of keys.filter((candidate) => candidate !== key)) {
      assert.equal(next.searchParams.get(preservedKey), FILTER_VALUES[preservedKey])
    }
    assert.equal(next.searchParams.get('page'), '1')
    assert.equal(next.searchParams.get('size'), '50')
    assert.equal(next.searchParams.get('tag'), null)
    assert.equal(next.searchParams.get('unknown'), null)
  }
})

test('전체 초기화는 구조화 필터를 모두 제거하고 size만 유지하며 첫 페이지로 이동한다', () => {
  const current = new URLSearchParams({
    ...FILTER_VALUES,
    page: '4',
    size: '10',
    tag: 'MENU_GUKBAP',
    returnTo: '/me',
  })

  assert.equal(
    buildRestaurantFiltersResetHref(current),
    '/restaurants?page=1&size=10',
  )
})

test('반복된 검색 조건은 URLSearchParams.get이 반환하는 첫 값만 유지한다', () => {
  const current = new URLSearchParams(
    'query=first&query=second&district=%EC%84%B1%EB%8F%99%EA%B5%AC&size=50',
  )

  assert.equal(
    buildRestaurantFilterClearHref(current, 'district'),
    '/restaurants?query=first&page=1&size=50',
  )
})

test('빈 구조화 필터는 전달하지 않고 빈 size는 기본값 20으로 대체한다', () => {
  const current = new URLSearchParams({
    query: '   ',
    district: '',
    category: '  ',
    creatorId: '',
    size: '   ',
  })

  assert.equal(
    buildRestaurantFilterClearHref(current, 'query'),
    '/restaurants?page=1&size=20',
  )
})

test('size가 없으면 현재 API 기본값 20을 명시한다', () => {
  const current = new URLSearchParams({ district: '마포구' })

  assert.equal(
    buildRestaurantFilterClearHref(current, 'category'),
    '/restaurants?district=%EB%A7%88%ED%8F%AC%EA%B5%AC&page=1&size=20',
  )
  assert.equal(
    buildRestaurantFiltersResetHref(current),
    '/restaurants?page=1&size=20',
  )
})

test('허용되지 않은 size는 필터 해제와 초기화에서 기본값 20으로 복구한다', () => {
  const current = new URLSearchParams({
    district: '성동구',
    page: '3',
    size: '7',
  })

  assert.equal(
    buildRestaurantFilterClearHref(current, 'district'),
    '/restaurants?page=1&size=20',
  )
  assert.equal(
    buildRestaurantFiltersResetHref(current),
    '/restaurants?page=1&size=20',
  )
})
