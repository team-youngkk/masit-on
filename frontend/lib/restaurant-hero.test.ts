import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildRestaurantHeroSlides,
  DEFAULT_RESTAURANT_HERO_IMAGE,
  getRestaurantHeroSlideSource,
  resolveRestaurantHeroImageError,
  shuffleRestaurantHeroSlides,
} from './restaurant-hero.ts'

const restaurants = [
  {
    id: 'restaurant-1',
    name: '첫 번째 맛집',
    category: '한식',
    representativeImageUrl: 'https://i.ytimg.com/vi/one/hqdefault.jpg',
  },
  {
    id: 'restaurant-2',
    name: '두 번째 맛집',
    category: '중식',
    representativeImageUrl: null,
  },
  {
    id: 'restaurant-3',
    name: '세 번째 맛집',
    category: '일식',
    representativeImageUrl: 'https://i.ytimg.com/vi/three/hqdefault.jpg',
  },
]

test('맛집 목록을 히어로 슬라이드와 안전한 fallback 이미지로 변환한다', () => {
  const slides = buildRestaurantHeroSlides(restaurants)

  assert.equal(slides[0].src, restaurants[0].representativeImageUrl)
  assert.match(slides[1].src, /\/chinese-food\//)
  assert.match(slides[1].fallbackSrc, /\/chinese-food\//)
  assert.equal(slides[0].name, restaurants[0].name)
})

test('히어로 슬라이드 순서를 복사본에서 섞고 원본은 보존한다', () => {
  const slides = buildRestaurantHeroSlides(restaurants)
  const originalIds = slides.map((slide) => slide.id)
  const shuffled = shuffleRestaurantHeroSlides(slides, () => 0)

  assert.deepEqual(slides.map((slide) => slide.id), originalIds)
  assert.notDeepEqual(shuffled.map((slide) => slide.id), originalIds)
  assert.deepEqual(
    new Set(shuffled.map((slide) => slide.id)),
    new Set(originalIds),
  )
})

test('슬라이드가 하나 이하면 같은 순서를 유지한다', () => {
  const slides = buildRestaurantHeroSlides(restaurants.slice(0, 1))

  assert.deepEqual(
    shuffleRestaurantHeroSlides(slides, () => 0).map((slide) => slide.id),
    ['restaurant-1'],
  )
  assert.deepEqual(shuffleRestaurantHeroSlides([], () => 0), [])
})

test('히어로 이미지 로드 실패는 placeholder와 기본 이미지 순서로 fallback한다', () => {
  const fallbackSrc = '/images/restaurant-placeholders/food-scenes-final/korean-food/01.webp'

  assert.equal(
    resolveRestaurantHeroImageError(
      'https://i.ytimg.com/vi/broken/hqdefault.jpg',
      fallbackSrc,
    ),
    fallbackSrc,
  )
  assert.equal(
    resolveRestaurantHeroImageError(fallbackSrc, fallbackSrc),
    DEFAULT_RESTAURANT_HERO_IMAGE,
  )
  assert.equal(
    resolveRestaurantHeroImageError(DEFAULT_RESTAURANT_HERO_IMAGE, fallbackSrc),
    DEFAULT_RESTAURANT_HERO_IMAGE,
  )
})

test('opaque 식별자는 fallback 상태 조회에서 객체 prototype으로 해석하지 않는다', () => {
  const slides = buildRestaurantHeroSlides([
    {
      id: 'toString',
      name: '문자열 맛집',
      category: '한식',
      representativeImageUrl: 'https://i.ytimg.com/valid.jpg',
    },
    {
      id: '__proto__',
      name: '프로토타입 맛집',
      category: '중식',
      representativeImageUrl: 'https://i.ytimg.com/prototype.jpg',
    },
    {
      id: 'constructor',
      name: '생성자 맛집',
      category: '일식',
      representativeImageUrl: 'https://i.ytimg.com/constructor.jpg',
    },
  ])
  const fallbackSources = {
    toString: '/fallback/to-string.webp',
    ['__proto__']: '/fallback/prototype.webp',
    constructor: '/fallback/constructor.webp',
  }

  assert.equal(
    getRestaurantHeroSlideSource(slides[0], {}),
    slides[0].src,
  )
  assert.equal(
    getRestaurantHeroSlideSource(slides[1], fallbackSources),
    '/fallback/prototype.webp',
  )
  assert.equal(
    getRestaurantHeroSlideSource(slides[2], fallbackSources),
    '/fallback/constructor.webp',
  )
})
