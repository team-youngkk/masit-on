import assert from 'node:assert/strict'
import test from 'node:test'

import { normalizePlaceSearchResponse } from './restaurant-place-search.ts'

test('빈 결과는 빈 배열로 처리한다', () => {
  assert.deepEqual(normalizePlaceSearchResponse({ items: [] }), [])
})

test('items가 배열이 아니면 빈 배열로 처리한다', () => {
  assert.deepEqual(normalizePlaceSearchResponse({ items: null }), [])
})

test('전화번호가 없는 항목은 null로 유지한다', () => {
  const result = normalizePlaceSearchResponse({
    items: [
      {
        placeName: '아코',
        kakaoPlaceUrl: 'https://place.map.kakao.com/example',
        roadAddress: '서울특별시 강동구 성내동 12-38',
        phoneNumber: null,
        district: '강동구',
      },
    ],
  })
  assert.deepEqual(result, [
    {
      placeName: '아코',
      kakaoPlaceUrl: 'https://place.map.kakao.com/example',
      roadAddress: '서울특별시 강동구 성내동 12-38',
      phoneNumber: null,
      district: '강동구',
    },
  ])
})

test('필수값이 없는 항목은 제외한다', () => {
  const result = normalizePlaceSearchResponse({
    items: [
      { placeName: '아코', roadAddress: '서울특별시 강동구 성내동 12-38', phoneNumber: null, district: null },
      { placeName: '아코', kakaoPlaceUrl: 'https://place.map.kakao.com/example', phoneNumber: null, district: null },
      null,
      'invalid',
      { placeName: '   ', kakaoPlaceUrl: 'https://place.map.kakao.com/example', roadAddress: '서울' },
      { placeName: '아코', kakaoPlaceUrl: '   ', roadAddress: '서울' },
      { placeName: '아코', kakaoPlaceUrl: 'https://place.map.kakao.com/example', roadAddress: '   ' },
    ],
  })
  assert.deepEqual(result, [])
})

test('검색 결과 문자열의 앞뒤 공백을 제거하고 빈 선택값은 null로 둔다', () => {
  assert.deepEqual(normalizePlaceSearchResponse({
    items: [{
      placeName: ' 아코 ',
      kakaoPlaceUrl: ' https://place.map.kakao.com/example ',
      roadAddress: ' 서울특별시 강동구 ',
      phoneNumber: ' 02-000-0000 ',
      district: '   ',
    }],
  }), [{
    placeName: '아코',
    kakaoPlaceUrl: 'https://place.map.kakao.com/example',
    roadAddress: '서울특별시 강동구',
    phoneNumber: '02-000-0000',
    district: null,
  }])
})

test('빈 전화번호 문자열은 누락과 같은 null로 정규화한다', () => {
  const [result] = normalizePlaceSearchResponse({
    items: [{ placeName: '아코', kakaoPlaceUrl: 'https://place.map.kakao.com/example', roadAddress: '서울', phoneNumber: '   ' }],
  })
  assert.equal(result.phoneNumber, null)
})
