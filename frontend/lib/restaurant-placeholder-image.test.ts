import assert from 'node:assert/strict'
import test from 'node:test'

import { getRestaurantPlaceholderImage } from './restaurant-placeholder-image.ts'

test('API 카테고리를 생성된 이미지 세트에 연결한다', () => {
  assert.equal(
    getRestaurantPlaceholderImage('restaurant-1', '한식').categorySlug,
    'korean-food',
  )
  assert.equal(
    getRestaurantPlaceholderImage('restaurant-2', '중식').categorySlug,
    'chinese-food',
  )
  assert.equal(
    getRestaurantPlaceholderImage('restaurant-3', '일식').categorySlug,
    'japanese-food',
  )
  assert.equal(
    getRestaurantPlaceholderImage('restaurant-4', '양식').categorySlug,
    'western-food',
  )
  assert.equal(
    getRestaurantPlaceholderImage('restaurant-5', '분식').categorySlug,
    'bunsik',
  )
  assert.equal(
    getRestaurantPlaceholderImage('restaurant-6', '동남아 음식').categorySlug,
    'seafood',
  )
  assert.equal(
    getRestaurantPlaceholderImage(
      'restaurant-7',
      '인도·남아시아 음식',
    ).categorySlug,
    'korean-food',
  )
  assert.equal(
    getRestaurantPlaceholderImage('restaurant-8', '카페·디저트').categorySlug,
    'cafe-drink',
  )
  assert.equal(
    getRestaurantPlaceholderImage('restaurant-9', '술집·주점').categorySlug,
    'japanese-food',
  )
  assert.equal(
    getRestaurantPlaceholderImage('restaurant-10', '기타').categorySlug,
    'korean-food',
  )
})

test('메뉴형 카테고리도 가장 가까운 이미지 세트로 자동 fallback한다', () => {
  assert.equal(
    getRestaurantPlaceholderImage('restaurant-6', '성수 곱창집').categorySlug,
    'meat',
  )
  assert.equal(
    getRestaurantPlaceholderImage('restaurant-7', '홍대 라멘').categorySlug,
    'noodles',
  )
  assert.equal(
    getRestaurantPlaceholderImage('restaurant-8', '신규 카페').categorySlug,
    'cafe-drink',
  )
})

test('지원하지 않는 신규 카테고리도 안전한 기본 이미지로 렌더링한다', () => {
  const image = getRestaurantPlaceholderImage('opaque-id', '새로운 음식')

  assert.equal(image.categorySlug, 'korean-food')
  assert.match(image.src, /\/korean-food\/(01|02|03)\.webp$/)
})

test('같은 맛집은 항상 같은 변형 이미지를 선택한다', () => {
  const first = getRestaurantPlaceholderImage('stable-id', '카페·디저트')
  const second = getRestaurantPlaceholderImage('stable-id', '카페·디저트')

  assert.deepEqual(second, first)
  assert.ok(first.variant >= 1 && first.variant <= 3)
})

test('맛집 ID가 달라지면 변형을 분산할 수 있다', () => {
  const variants = new Set(
    Array.from({ length: 12 }, (_, index) =>
      getRestaurantPlaceholderImage(`restaurant-${index}`, '한식').variant,
    ),
  )

  assert.equal(variants.size, 3)
})
