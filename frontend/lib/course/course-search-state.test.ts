import assert from 'node:assert/strict'
import test from 'node:test'

import {
  courseScreenSearchFiltersKey,
  isCurrentCourseSearchRequest,
  normalizeCourseScreenSearchFilters,
  resetCourseSearchState,
} from './course-search-state.ts'

test('URL에서 받은 코스 검색 조건은 화면용 문자열 필터로 정규화한다', () => {
  assert.deepEqual(
    normalizeCourseScreenSearchFilters({
      query: '  국밥 ',
      regionCode: ' 3611000000 ',
      category: ' 한식 ',
    }),
    { query: '국밥', regionCode: '3611000000', category: '한식' },
  )
})

test('URL 지역이 바뀌면 화면 검색 상태 키도 바뀌고 같은 URL은 유지된다', () => {
  const seoul = { query: '', regionCode: '1100000000', category: '' }
  const busan = { query: '', regionCode: '2600000000', category: '' }

  assert.notEqual(courseScreenSearchFiltersKey(seoul), courseScreenSearchFiltersKey(busan))
  assert.equal(courseScreenSearchFiltersKey(seoul), courseScreenSearchFiltersKey({ ...seoul }))
})

test('URL 필터가 바뀌면 검색 결과를 1페이지 idle 상태로 초기화한다', () => {
  assert.deepEqual(resetCourseSearchState({ query: '', regionCode: '3611000000', category: '' }), {
    status: 'idle',
    items: [],
    page: 1,
    hasNext: false,
    loadingMore: false,
    filters: { query: '', regionCode: '3611000000', category: '' },
  })
})

test('새 지역 요청이 시작되면 이전 지역 요청의 응답을 반영하지 않는다', () => {
  assert.equal(isCurrentCourseSearchRequest(2, 1), false)
  assert.equal(isCurrentCourseSearchRequest(2, 2), true)
})
