import assert from 'node:assert/strict'
import test from 'node:test'

import robots from './robots.ts'
import sitemap from './sitemap.ts'
import {
  buildRestaurantDetailMetadata,
  buildRestaurantsMetadata,
} from '../lib/restaurant-seo.ts'

function withSiteUrl<T>(callback: () => T): T {
  const previousSiteUrl = process.env['NEXT_PUBLIC_SITE_URL']
  process.env['NEXT_PUBLIC_SITE_URL'] = 'https://masit-on.example'

  try {
    return callback()
  } finally {
    process.env['NEXT_PUBLIC_SITE_URL'] = previousSiteUrl
  }
}

test('robots는 맛집 공개 경로와 Next 정적 번들만 크롤링에 허용한다', () => {
  const previousSiteUrl = process.env['NEXT_PUBLIC_SITE_URL']
  process.env['NEXT_PUBLIC_SITE_URL'] = 'https://masit-on.example'

  try {
    assert.deepEqual(robots(), {
      rules: [
        {
          userAgent: '*',
          allow: ['/restaurants', '/restaurants/', '/sitemap.xml', '/_next/'],
          disallow: '/',
        },
      ],
      sitemap: 'https://masit-on.example/sitemap.xml',
    })
  } finally {
    process.env['NEXT_PUBLIC_SITE_URL'] = previousSiteUrl
  }
})

test('sitemap은 모든 페이지의 공개 맛집을 한 번씩만 포함한다', async () => {
  const previousSiteUrl = process.env['NEXT_PUBLIC_SITE_URL']
  const previousFetch = globalThis.fetch
  process.env['NEXT_PUBLIC_SITE_URL'] = 'https://masit-on.example'
  globalThis.fetch = async (input) => {
    const url = new URL(String(input))
    const page = url.searchParams.get('page')
    const body =
      page === '1'
        ? {
            items: [{ id: 'restaurant-a' }, { id: 'restaurant-b' }],
            page: { hasNext: true, totalPages: 2 },
          }
        : {
            items: [{ id: 'restaurant-b' }, { id: 'restaurant-c' }],
            page: { hasNext: false, totalPages: 2 },
          }

    return new Response(JSON.stringify(body), { status: 200 })
  }

  try {
    assert.deepEqual(await sitemap(), [
      { url: 'https://masit-on.example/restaurants' },
      { url: 'https://masit-on.example/restaurants/restaurant-a' },
      { url: 'https://masit-on.example/restaurants/restaurant-b' },
      { url: 'https://masit-on.example/restaurants/restaurant-c' },
    ])
  } finally {
    process.env['NEXT_PUBLIC_SITE_URL'] = previousSiteUrl
    globalThis.fetch = previousFetch
  }
})

test('sitemap API가 실패해도 기본 목록 URL을 담은 200 응답용 결과를 만든다', async () => {
  const previousSiteUrl = process.env['NEXT_PUBLIC_SITE_URL']
  const previousFetch = globalThis.fetch
  process.env['NEXT_PUBLIC_SITE_URL'] = 'https://masit-on.example'
  globalThis.fetch = async () => new Response(null, { status: 503 })

  try {
    assert.deepEqual(await sitemap(), [
      { url: 'https://masit-on.example/restaurants' },
    ])
  } finally {
    process.env['NEXT_PUBLIC_SITE_URL'] = previousSiteUrl
    globalThis.fetch = previousFetch
  }
})

test('검색 조건과 빈 목록·오류는 색인하지 않고 기본 목록의 canonical은 유지한다', () => {
  withSiteUrl(() => {
    assert.deepEqual(
      buildRestaurantsMetadata(
        { query: '냉면' },
        { requestSucceeded: true, hasItems: true },
      ),
      {
        alternates: { canonical: 'https://masit-on.example/restaurants' },
        robots: { index: false, follow: true },
      },
    )
    assert.deepEqual(
      buildRestaurantsMetadata(
        {},
        { requestSucceeded: true, hasItems: false },
      ),
      {
        alternates: { canonical: 'https://masit-on.example/restaurants' },
        robots: { index: false, follow: false },
      },
    )
    assert.deepEqual(
      buildRestaurantsMetadata(
        {},
        { requestSucceeded: false, hasItems: false },
      ),
      {
        alternates: { canonical: 'https://masit-on.example/restaurants' },
        robots: { index: false, follow: false },
      },
    )
  })
})

test('데이터가 있는 기본 목록만 색인과 follow를 허용한다', () => {
  withSiteUrl(() => {
    assert.deepEqual(
      buildRestaurantsMetadata(
        {},
        { requestSucceeded: true, hasItems: true },
      ),
      {
        title: '유튜버가 방문한 맛집 탐색 | 맛잇온',
        description: '유튜버가 방문한 서울 맛집을 지역, 음식 종류, 유튜버로 탐색하세요.',
        robots: { index: true, follow: true },
        alternates: { canonical: 'https://masit-on.example/restaurants' },
      },
    )
  })
})

test('상세 기본 URL만 색인하고 쿼리 변형은 색인하지 않는다', () => {
  withSiteUrl(() => {
    const canonical = 'https://masit-on.example/restaurants/restaurant-a'
    const restaurant = { name: '맛있는 식당', category: '한식' }

    assert.deepEqual(
      buildRestaurantDetailMetadata({}, canonical, restaurant),
      {
        title: '맛있는 식당 | 한식 맛집 | 맛잇온',
        description: '유튜버가 방문한 한식 맛집 맛있는 식당의 정보를 확인하세요.',
        robots: { index: true, follow: true },
        alternates: { canonical },
      },
    )
    assert.deepEqual(
      buildRestaurantDetailMetadata({ ref: 'search' }, canonical, restaurant),
      {
        title: '맛있는 식당 | 한식 맛집 | 맛잇온',
        description: '유튜버가 방문한 한식 맛집 맛있는 식당의 정보를 확인하세요.',
        robots: { index: false, follow: true },
        alternates: { canonical },
      },
    )
    assert.deepEqual(
      buildRestaurantDetailMetadata({ ref: 'search' }, canonical),
      {
        alternates: { canonical },
        robots: { index: false, follow: false },
      },
    )
    assert.deepEqual(
      buildRestaurantDetailMetadata({ ref: 'search' }, null, restaurant),
      {
        robots: { index: false, follow: false },
      },
    )
  })
})
