/*
 * 코스 후보 선택 상태 전이 전용 순수 함수.
 * 계약: docs/04-product/wireframes/third-expansion-wireframes.md 5절(COURSE-BUILDER)
 *       docs/05-specs/api/discovery/restaurant-course-recommendation-api.md 4절
 *       (restaurantIds는 2~5개, 중복 불가)
 * 선택 상태는 서버에 저장하지 않고 화면 안에서만 유지한다.
 */

export type CourseCandidate = {
  id: string
  name: string
  district: string
  category: string
}

export const MIN_COURSE_SIZE = 2
export const MAX_COURSE_SIZE = 5

export type CourseMemberRole = 'MEMBER' | 'ADMIN'

export function canUseCourseFavoriteSource(
  memberStatus: string,
  memberRole: CourseMemberRole | undefined,
): boolean {
  return memberStatus === 'authenticated' && memberRole === 'MEMBER'
}

export function isCourseFull(selected: CourseCandidate[]): boolean {
  return selected.length >= MAX_COURSE_SIZE
}

export function isCourseCandidateSelected(
  selected: CourseCandidate[],
  candidateId: string,
): boolean {
  return selected.some((item) => item.id === candidateId)
}

export type CourseCandidateActionState = {
  alreadySelected: boolean
  full: boolean
  disabled: boolean
}

export function courseCandidateActionState(
  selected: CourseCandidate[],
  candidateId: string,
): CourseCandidateActionState {
  const alreadySelected = isCourseCandidateSelected(selected, candidateId)
  const full = isCourseFull(selected)
  return { alreadySelected, full, disabled: alreadySelected || full }
}

/* 페이지를 이어 받을 때 같은 맛집 ID가 다시 오더라도 후보 원천의 순서를 유지한다. */
export function appendUniqueCourseCandidates(
  current: CourseCandidate[],
  additions: CourseCandidate[],
): CourseCandidate[] {
  const ids = new Set(current.map((item) => item.id))
  const unique = additions.filter((item) => {
    if (ids.has(item.id)) return false
    ids.add(item.id)
    return true
  })
  return unique.length === 0 ? current : [...current, ...unique]
}

/* 이미 선택됐거나 5개가 찬 상태에서 추가를 요청하면 목록을 바꾸지 않는다. */
export function addCourseCandidate(
  selected: CourseCandidate[],
  candidate: CourseCandidate,
): CourseCandidate[] {
  if (isCourseFull(selected) || isCourseCandidateSelected(selected, candidate.id)) {
    return selected
  }
  return [...selected, candidate]
}

export function removeCourseCandidateAt(
  selected: CourseCandidate[],
  index: number,
): CourseCandidate[] {
  if (index < 0 || index >= selected.length) {
    return selected
  }
  return [...selected.slice(0, index), ...selected.slice(index + 1)]
}

/*
 * 첫 항목(index 0)은 출발점으로 고정 표시되지만 순서 자체는 바꿀 수 있다.
 * 순서를 바꿔 새 첫 항목이 생기면 그 항목이 새 출발점이 된다
 * (user-flows.md 4.1절).
 */
export function moveCourseCandidate(
  selected: CourseCandidate[],
  index: number,
  direction: -1 | 1,
): CourseCandidate[] {
  const target = index + direction
  if (
    index < 0 ||
    index >= selected.length ||
    target < 0 ||
    target >= selected.length
  ) {
    return selected
  }
  const next = [...selected]
  const temp = next[index]
  next[index] = next[target]
  next[target] = temp
  return next
}

export function canCalculateCourse(selected: CourseCandidate[]): boolean {
  return selected.length >= MIN_COURSE_SIZE && selected.length <= MAX_COURSE_SIZE
}

export type CourseSizeGuidance = {
  code: 'BELOW_MINIMUM' | 'AT_MAXIMUM'
  message: string
} | null

/* 계산·추가 버튼을 비활성화할 때 인접 설명으로 쓸 문구. 활성 상태면 null이다. */
export function courseSizeGuidance(selected: CourseCandidate[]): CourseSizeGuidance {
  if (selected.length < MIN_COURSE_SIZE) {
    return {
      code: 'BELOW_MINIMUM',
      message: '코스를 계산하려면 맛집을 최소 2개 선택해야 합니다.',
    }
  }
  if (selected.length >= MAX_COURSE_SIZE) {
    return {
      code: 'AT_MAXIMUM',
      message: '최대 5개까지 선택할 수 있습니다. 더 추가하려면 기존 항목을 먼저 제거해 주세요.',
    }
  }
  return null
}

export function toCourseRestaurantIds(selected: CourseCandidate[]): string[] {
  return selected.map((item) => item.id)
}
