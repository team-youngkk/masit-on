import assert from 'node:assert/strict'
import test from 'node:test'

import {
  getRestaurantImageSource,
  resolveRestaurantImageError,
} from './restaurant-image.ts'

test('유효한 대표 영상 썸네일을 우선 사용한다', () => {
  const source = getRestaurantImageSource({
    restaurantId: 'restaurant-1',
    category: '한식',
    representativeImageUrl: 'https://i.ytimg.com/vi/video-1/hqdefault.jpg',
  })

  assert.equal(source.src, 'https://i.ytimg.com/vi/video-1/hqdefault.jpg')
  assert.match(source.fallbackSrc, /\/korean-food\//)
})

test('대표 썸네일이 없거나 안전하지 않으면 카테고리 placeholder를 사용한다', () => {
  const empty = getRestaurantImageSource({
    restaurantId: 'restaurant-1',
    category: '한식',
    representativeImageUrl: null,
  })
  const unsafe = getRestaurantImageSource({
    restaurantId: 'restaurant-1',
    category: '한식',
    representativeImageUrl: 'javascript:alert(1)',
  })

  assert.equal(empty.src, empty.fallbackSrc)
  assert.equal(unsafe.src, unsafe.fallbackSrc)
})

test('대표 썸네일 로드 실패 시 placeholder로 전환하고 재실패에는 같은 값을 유지한다', () => {
  const fallbackSrc = '/images/restaurant-placeholders/food-scenes-final/korean-food/01.webp'

  assert.equal(
    resolveRestaurantImageError('https://i.ytimg.com/broken.jpg', fallbackSrc),
    fallbackSrc,
  )
  assert.equal(resolveRestaurantImageError(fallbackSrc, fallbackSrc), fallbackSrc)
})
