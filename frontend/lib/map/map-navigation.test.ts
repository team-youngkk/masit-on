import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildMapNavigationHref,
  buildMapNavigationHrefAfterRegionClear,
  getMapUnsupportedRegionCode,
} from './map-navigation.ts'

test('지도 지역 코드 판정은 반복 값과 공백을 안전하게 처리한다', () => {
  assert.equal(getMapUnsupportedRegionCode([' 4111000000 ', '1114000000']), '4111000000')
  assert.equal(getMapUnsupportedRegionCode(['', '4111000000']), '4111000000')
  assert.equal(getMapUnsupportedRegionCode(''), null)
  assert.equal(getMapUnsupportedRegionCode(['']), null)
  assert.equal(getMapUnsupportedRegionCode(undefined), null)
})

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

test('전국 지역 코드가 있으면 다른 탐색 조건이 있어도 지도 이동을 막는다', () => {
  for (const regionCode of ['4111000000', '3611000000', '1114000000']) {
    const params = new URLSearchParams({
      regionCode,
      query: '국밥',
      district: '수원시',
      category: '한식',
      creatorId: 'opaque-creator-id',
    })

    assert.equal(buildMapNavigationHref('/restaurants', params), null)
  }

  const blankFirstRegionCode = new URLSearchParams(
    'regionCode=&regionCode=4111000000&query=국밥',
  )
  assert.equal(buildMapNavigationHref('/restaurants', blankFirstRegionCode), null)
})

test('기존 서울 자치구 조건만 있으면 지도 이동을 유지한다', () => {
  const params = new URLSearchParams({ district: '성동구' })

  assert.equal(
    buildMapNavigationHref('/restaurants', params),
    '/map?district=%EC%84%B1%EB%8F%99%EA%B5%AC',
  )
})

test('지역 필터를 해제한 지도 링크는 지도 조건만 유지한다', () => {
  const params = new URLSearchParams({
    regionCode: '4111000000',
    query: '국밥',
    category: '한식',
    page: '3',
  })

  assert.equal(
    buildMapNavigationHrefAfterRegionClear('/map', params),
    '/map?query=%EA%B5%AD%EB%B0%A5&category=%ED%95%9C%EC%8B%9D',
  )
})
