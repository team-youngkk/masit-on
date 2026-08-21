import assert from 'node:assert/strict'
import test from 'node:test'

import {
  isDesignPreviewEnvironment,
  shouldUseRestaurantDesignPreview,
} from './design-preview.ts'

test('운영 환경에서는 미리보기 플래그가 있어도 디자인 미리보기를 끈다', () => {
  assert.equal(
    isDesignPreviewEnvironment({ nodeEnv: 'production', previewFlag: '1' }),
    false,
  )
})

test('개발 환경에서만 디자인 미리보기를 켠다', () => {
  assert.equal(
    isDesignPreviewEnvironment({ nodeEnv: 'development', previewFlag: '1' }),
    true,
  )
  assert.equal(
    isDesignPreviewEnvironment({ nodeEnv: 'development', previewFlag: '0' }),
    false,
  )
})

test('공백 검색어는 빈 검색으로 보고 맛집 미리보기를 유지한다', () => {
  assert.equal(
    shouldUseRestaurantDesignPreview({
      nodeEnv: 'development',
      previewFlag: '1',
      hasItems: false,
      query: '   ',
      district: '',
      category: '',
      creatorId: null,
    }),
    true,
  )
})

test('검색 조건이나 실제 결과가 있으면 맛집 미리보기를 끈다', () => {
  const base = {
    nodeEnv: 'development',
    previewFlag: '1',
    hasItems: false,
    query: '',
    district: '',
    category: '',
    creatorId: null,
  }

  assert.equal(shouldUseRestaurantDesignPreview({ ...base, query: '곱창' }), false)
  assert.equal(shouldUseRestaurantDesignPreview({ ...base, hasItems: true }), false)
})
