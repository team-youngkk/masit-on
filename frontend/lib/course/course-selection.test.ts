import assert from 'node:assert/strict'
import test from 'node:test'

import {
  MAX_COURSE_SIZE,
  MIN_COURSE_SIZE,
  addCourseCandidate,
  canCalculateCourse,
  courseSizeGuidance,
  isCourseCandidateSelected,
  isCourseFull,
  moveCourseCandidate,
  removeCourseCandidateAt,
  toCourseRestaurantIds,
  type CourseCandidate,
} from './course-selection.ts'

function candidate(id: string): CourseCandidate {
  return { id, name: `맛집 ${id}`, district: '성동구', category: '한식' }
}

test('선택 목록에 새 후보를 추가한다', () => {
  const selected = addCourseCandidate([candidate('A')], candidate('B'))
  assert.deepEqual(toCourseRestaurantIds(selected), ['A', 'B'])
})

test('이미 선택된 후보는 다시 추가되지 않는다', () => {
  const selected = addCourseCandidate([candidate('A')], candidate('A'))
  assert.deepEqual(toCourseRestaurantIds(selected), ['A'])
})

test('5개가 찬 목록에는 추가할 수 없다', () => {
  const full = ['A', 'B', 'C', 'D', 'E'].map(candidate)
  assert.equal(isCourseFull(full), true)
  const selected = addCourseCandidate(full, candidate('F'))
  assert.deepEqual(toCourseRestaurantIds(selected), ['A', 'B', 'C', 'D', 'E'])
})

test('isCourseCandidateSelected는 id 일치 여부만 본다', () => {
  const selected = [candidate('A')]
  assert.equal(isCourseCandidateSelected(selected, 'A'), true)
  assert.equal(isCourseCandidateSelected(selected, 'B'), false)
})

test('지정한 위치의 후보를 제거한다', () => {
  const selected = [candidate('A'), candidate('B'), candidate('C')]
  assert.deepEqual(
    toCourseRestaurantIds(removeCourseCandidateAt(selected, 1)),
    ['A', 'C'],
  )
})

test('범위를 벗어난 제거 요청은 목록을 바꾸지 않는다', () => {
  const selected = [candidate('A')]
  assert.equal(removeCourseCandidateAt(selected, 5), selected)
  assert.equal(removeCourseCandidateAt(selected, -1), selected)
})

test('위로·아래로 이동은 인접 항목과 자리를 바꾼다', () => {
  const selected = [candidate('A'), candidate('B'), candidate('C')]
  assert.deepEqual(
    toCourseRestaurantIds(moveCourseCandidate(selected, 1, -1)),
    ['B', 'A', 'C'],
  )
  assert.deepEqual(
    toCourseRestaurantIds(moveCourseCandidate(selected, 1, 1)),
    ['A', 'C', 'B'],
  )
})

test('첫 항목을 위로, 마지막 항목을 아래로 이동하려 하면 목록을 바꾸지 않는다', () => {
  const selected = [candidate('A'), candidate('B')]
  assert.equal(moveCourseCandidate(selected, 0, -1), selected)
  assert.equal(moveCourseCandidate(selected, 1, 1), selected)
})

test('2개 미만이거나 5개를 넘으면 계산할 수 없다', () => {
  assert.equal(canCalculateCourse([]), false)
  assert.equal(canCalculateCourse([candidate('A')]), false)
  assert.equal(canCalculateCourse(['A', 'B'].map(candidate)), true)
  assert.equal(canCalculateCourse(['A', 'B', 'C', 'D', 'E'].map(candidate)), true)
})

test('1개 이하는 최소 개수 안내를, 5개는 최대 개수 안내를 반환한다', () => {
  assert.equal(courseSizeGuidance([candidate('A')])?.code, 'BELOW_MINIMUM')
  assert.equal(
    courseSizeGuidance(['A', 'B', 'C', 'D', 'E'].map(candidate))?.code,
    'AT_MAXIMUM',
  )
  assert.equal(courseSizeGuidance(['A', 'B'].map(candidate)), null)
})

test('MIN_COURSE_SIZE와 MAX_COURSE_SIZE는 계약값 2와 5를 유지한다', () => {
  assert.equal(MIN_COURSE_SIZE, 2)
  assert.equal(MAX_COURSE_SIZE, 5)
})
