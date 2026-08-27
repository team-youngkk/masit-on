import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildPageViewParams,
  isValidGa4MeasurementId,
  toAnalyticsPathname,
} from './analytics.ts'

test('GA4 측정 ID 형식을 검증한다', () => {
  assert.equal(isValidGa4MeasurementId('G-ABC123'), true)
  assert.equal(isValidGa4MeasurementId(' G-ABC123 '), true)
  assert.equal(isValidGa4MeasurementId('UA-123'), false)
  assert.equal(isValidGa4MeasurementId(undefined), false)
})

test('동적 경로를 라우트 템플릿으로 치환한다', () => {
  assert.equal(toAnalyticsPathname('/restaurants/opaque-id'), '/restaurants/[id]')
  assert.equal(toAnalyticsPathname('/creators/channel-id'), '/creators/[id]')
  assert.equal(toAnalyticsPathname('/curations/curation-id'), '/curations/[curationId]')
  assert.equal(toAnalyticsPathname('/me/collections/member-id'), '/me/collections/[id]')
  assert.equal(toAnalyticsPathname('/admin/ai/job-id'), '/admin/ai/[jobId]')
})

test('페이지뷰 파라미터에서 쿼리와 식별자 원문을 제거한다', () => {
  assert.deepEqual(
    buildPageViewParams(
      '/restaurants/restaurant-id?query=개인검색어#section',
      'https://masit-on.example',
    ),
    {
      page_path: '/restaurants/[id]',
      page_location: 'https://masit-on.example/restaurants/[id]',
      page_title: '맛잇온',
      page_referrer: '',
    },
  )
})
