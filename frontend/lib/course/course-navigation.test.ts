import assert from 'node:assert/strict'
import test from 'node:test'

import { COURSE_NAVIGATION } from './course-navigation.ts'

test('공개 사이트 헤더의 코스 진입점은 코스 화면으로 연결된다', () => {
  assert.deepEqual(COURSE_NAVIGATION, { href: '/course', label: '맛집 코스' })
})
