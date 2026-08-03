import assert from 'node:assert/strict'
import test from 'node:test'

import { buildMapNavigationHref } from './map-navigation.ts'

test('맛집 목록의 공개 검색 조건을 지도 링크에 유지한다', () => {
  const params = new URLSearchParams({
    query: '성수 맛집',
    district: '성동구',
    category: '한식',
    creatorId: '8b5de981-3657-40a6-a9ab-e4b15caf72b5',
    page: '3',
    size: '50',
  })

  assert.equal(
    buildMapNavigationHref('/restaurants', params),
    '/map?query=%EC%84%B1%EC%88%98+%EB%A7%9B%EC%A7%91&district=%EC%84%B1%EB%8F%99%EA%B5%AC&category=%ED%95%9C%EC%8B%9D&creatorId=8b5de981-3657-40a6-a9ab-e4b15caf72b5',
  )
})

test('지도에서 다시 지도 메뉴를 눌러도 적용 중인 조건을 유지한다', () => {
  const params = new URLSearchParams({ creatorId: 'creator-id' })

  assert.equal(
    buildMapNavigationHref('/map', params),
    '/map?creatorId=creator-id',
  )
})

test('공개 탐색 화면 밖에서는 검색 파라미터를 지도에 전달하지 않는다', () => {
  const params = new URLSearchParams({ creatorId: 'creator-id', returnTo: '/me' })

  assert.equal(buildMapNavigationHref('/login', params), '/map')
})

test('빈 검색 조건은 생략한다', () => {
  const params = new URLSearchParams({ query: '   ', creatorId: '' })

  assert.equal(buildMapNavigationHref('/restaurants', params), '/map')
})
