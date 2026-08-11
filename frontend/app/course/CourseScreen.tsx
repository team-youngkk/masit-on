'use client'

import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { CATEGORY_OPTIONS, DISTRICT_OPTIONS } from '@/lib/restaurants-api'
import {
  MAX_COURSE_SIZE,
  addCourseCandidate,
  canCalculateCourse,
  courseSizeGuidance,
  isCourseCandidateSelected,
  isCourseFull,
  moveCourseCandidate,
  removeCourseCandidateAt,
  toCourseRestaurantIds,
  type CourseCandidate,
} from '@/lib/course/course-selection'
import { requestCourseRoute } from '@/lib/course/course-route-api'
import {
  searchCourseCandidates,
  type CourseSearchItem,
} from '@/lib/course/course-search-api'
import {
  courseFailureCategoryLabel,
  courseInvalidCategoryLabel,
  didCourseSelectionChange,
  formatCourseDistance,
  formatCourseDuration,
  isCourseRouteExpired,
  msUntilCourseRouteExpiry,
  type CourseRouteOutcome,
} from '@/lib/course/course-screen-state'

import styles from './course.module.css'

/* 만료 여부를 반영하기 위해 이 주기로 현재 시각을 다시 읽는다. 실시간 카운트다운은 아니다. */
const EXPIRY_CHECK_INTERVAL_MS = 15_000

type SearchState = {
  status: 'idle' | 'loading' | 'loaded' | 'error'
  items: CourseSearchItem[]
  message?: string
  traceId?: string
}

export function CourseScreen() {
  const [selected, setSelected] = useState<CourseCandidate[]>([])
  const [query, setQuery] = useState('')
  const [district, setDistrict] = useState('')
  const [category, setCategory] = useState('')
  const [search, setSearch] = useState<SearchState>({ status: 'idle', items: [] })
  const [outcome, setOutcome] = useState<CourseRouteOutcome | null>(null)
  const [calculating, setCalculating] = useState(false)
  const [now, setNow] = useState(() => Date.now())

  const searchRequestId = useRef(0)
  const routeRequestId = useRef(0)
  /* 같은 tick에 연속으로 발생한 선택 변경이 서로를 덮어쓰지 않도록 최신 목록을 따로 들고 있는다. */
  const selectedRef = useRef<CourseCandidate[]>(selected)

  useEffect(() => {
    if (outcome?.kind !== 'success') {
      return
    }
    const timer = window.setInterval(() => setNow(Date.now()), EXPIRY_CHECK_INTERVAL_MS)
    return () => window.clearInterval(timer)
  }, [outcome])

  /*
   * 15초 폴링만으로는 만료 시각과 다음 tick 사이 최대 15초 동안 만료된 결과가 정상처럼
   * 보일 수 있다. 만료 시각에 정확히 맞춰 한 번 더 갱신해 이 창을 없앤다.
   */
  useEffect(() => {
    if (outcome?.kind !== 'success') {
      return
    }
    const delay = msUntilCourseRouteExpiry(outcome.route.expiresAt, Date.now())
    if (delay === null) {
      return
    }
    const timeout = window.setTimeout(() => setNow(Date.now()), delay)
    return () => window.clearTimeout(timeout)
  }, [outcome])

  useEffect(
    () => () => {
      searchRequestId.current += 1
      routeRequestId.current += 1
    },
    [],
  )

  async function runSearch() {
    const requestId = ++searchRequestId.current
    setSearch({ status: 'loading', items: [] })
    let settled = false
    try {
      const result = await searchCourseCandidates({ query, district, category })
      if (searchRequestId.current !== requestId) {
        return
      }
      settled = true
      if (result.ok) {
        setSearch({ status: 'loaded', items: result.items })
      } else {
        setSearch({ status: 'error', items: [], message: result.message, traceId: result.traceId })
      }
    } finally {
      /*
       * 정상 흐름은 위에서 이미 loaded·error로 상태를 옮겼으므로(settled === true) 여기서는
       * 아무 것도 하지 않는다. searchCourseCandidates가 의도적으로 rethrow하는
       * AbortError(예외)로 함수가 중간에 빠져나갈 때만 이 블록이 "검색 중…" 고착을 막는다.
       */
      if (!settled && searchRequestId.current === requestId) {
        setSearch({ status: 'idle', items: [] })
      }
    }
  }

  /*
   * 선택 변경은 항상 `selectedRef`의 최신 목록에서 계산한다. `selected` state를 그대로 읽으면
   * 같은 tick에 연속으로 눌린 추가·삭제가 모두 같은 렌더의 값을 보고 마지막 것만 남는다.
   * setState updater 안에서 계산하면 이 문제는 없지만, updater가 순수해야 하는 제약 때문에
   * 요청 무효화 같은 부수효과를 그 안에 둘 수 없다. ref는 두 조건을 모두 만족한다.
   *
   * 선택 목록이 실제로 바뀐 경우에만 진행 중인 계산 요청과 기존 결과를 무효화한다. 이미
   * 선택됨·5개 상한 초과로 목록이 그대로면 표시 중인 결과를 지우지 않는다.
   */
  function applySelectionChange(
    compute: (current: CourseCandidate[]) => CourseCandidate[],
  ) {
    const previous = selectedRef.current
    const next = compute(previous)
    if (!didCourseSelectionChange(previous, next)) {
      return
    }
    selectedRef.current = next
    routeRequestId.current += 1
    setOutcome(null)
    setSelected(next)
  }

  function addCandidate(item: CourseSearchItem) {
    applySelectionChange((current) => addCourseCandidate(current, item))
  }

  function removeCandidate(index: number) {
    applySelectionChange((current) => removeCourseCandidateAt(current, index))
  }

  function moveCandidate(index: number, direction: -1 | 1) {
    applySelectionChange((current) => moveCourseCandidate(current, index, direction))
  }

  async function calculateCourse() {
    if (!canCalculateCourse(selected) || calculating) {
      return
    }
    const requestId = ++routeRequestId.current
    setCalculating(true)
    try {
      const result = await requestCourseRoute(toCourseRestaurantIds(selected))
      if (routeRequestId.current !== requestId) {
        return
      }
      setNow(Date.now())
      setOutcome(result)
    } finally {
      setCalculating(false)
    }
  }

  /*
   * 사용자가 명시적으로 결과 패널을 닫는 동작이다. 진행 중인 계산 요청의 응답이 나중에
   * 도착하더라도 이 시점 이후의 결과로 되살아나지 않도록 요청 ID도 함께 무효화한다.
   */
  function backToBuilder() {
    routeRequestId.current += 1
    setOutcome(null)
  }

  const sizeGuidance = courseSizeGuidance(selected)
  const calculateDisabled = !canCalculateCourse(selected) || calculating
  const showResult = outcome?.kind === 'success'
  const expired = showResult && isCourseRouteExpired(outcome.route.expiresAt, now)

  return (
    <div className={styles.layout}>
      <section className={styles.searchPanel} aria-labelledby="course-search-heading">
        <h2 id="course-search-heading">맛집 더 찾기</h2>
        <form
          className={styles.searchForm}
          onSubmit={(event) => {
            event.preventDefault()
            void runSearch()
          }}
        >
          <Field
            label="맛집 이름"
            name="query"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="예: 강된장"
            maxLength={100}
          />
          <div className={styles.selectGroup}>
            <label className={styles.selectLabel} htmlFor="course-district">
              자치구
            </label>
            <select
              id="course-district"
              className={styles.select}
              value={district}
              onChange={(event) => setDistrict(event.target.value)}
            >
              <option value="">전체</option>
              {DISTRICT_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
          <div className={styles.selectGroup}>
            <label className={styles.selectLabel} htmlFor="course-category">
              대표 음식
            </label>
            <select
              id="course-category"
              className={styles.select}
              value={category}
              onChange={(event) => setCategory(event.target.value)}
            >
              <option value="">전체</option>
              {CATEGORY_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
          <Button type="submit" className={styles.searchSubmit} disabled={search.status === 'loading'}>
            {search.status === 'loading' ? '검색 중…' : '검색'}
          </Button>
        </form>

        {search.status === 'error' ? (
          <p className={styles.error} role="alert">
            {search.message}
            {search.traceId ? <span className={styles.traceId}>traceId: {search.traceId}</span> : null}
          </p>
        ) : null}

        {search.status === 'loaded' && search.items.length === 0 ? (
          <p className={styles.state}>조건에 맞는 맛집이 없습니다.</p>
        ) : null}

        {search.status === 'loaded' && search.items.length > 0 ? (
          <ul className={styles.searchResults}>
            {search.items.map((item) => {
              const alreadySelected = isCourseCandidateSelected(selected, item.id)
              const full = isCourseFull(selected)
              const addDisabled = alreadySelected || full
              return (
                <li key={item.id} className={styles.searchResultItem}>
                  <div>
                    <p className={styles.searchResultName}>{item.name}</p>
                    <p className={styles.searchResultMeta}>
                      {item.district} · {item.category}
                    </p>
                  </div>
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={addDisabled}
                    aria-describedby={full ? 'course-add-guidance' : undefined}
                    onClick={() => addCandidate(item)}
                  >
                    {alreadySelected ? '선택됨' : '코스에 추가'}
                  </Button>
                </li>
              )
            })}
          </ul>
        ) : null}

        {isCourseFull(selected) ? (
          <p id="course-add-guidance" className={styles.selectHint}>
            {courseSizeGuidance(selected)?.message}
          </p>
        ) : null}
      </section>

      <section className={styles.builderPanel} aria-labelledby="course-builder-heading">
        <div className={styles.builderHeader}>
          <h2 id="course-builder-heading">맛집 코스</h2>
          <p className={styles.selectionCount}>
            선택 {selected.length}/{MAX_COURSE_SIZE}
          </p>
        </div>

        {selected.length === 0 ? (
          <p className={styles.state}>왼쪽에서 맛집을 검색해 코스에 추가해 주세요.</p>
        ) : (
          <ol className={styles.selectionList}>
            {selected.map((item, index) => (
              <li key={item.id} className={styles.selectionItem}>
                <span className={styles.selectionOrder}>{index + 1}</span>
                <div className={styles.selectionInfo}>
                  {index === 0 ? <span className={styles.startBadge}>출발</span> : null}
                  <p className={styles.selectionName}>{item.name}</p>
                  <p className={styles.searchResultMeta}>
                    {item.district} · {item.category}
                  </p>
                </div>
                <div className={styles.selectionActions}>
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={index === 0}
                    aria-label={`${item.name} 위로 이동`}
                    onClick={() => moveCandidate(index, -1)}
                  >
                    위로
                  </Button>
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={index === selected.length - 1}
                    aria-label={`${item.name} 아래로 이동`}
                    onClick={() => moveCandidate(index, 1)}
                  >
                    아래로
                  </Button>
                  <Button
                    type="button"
                    variant="secondary"
                    aria-label={`${item.name} 코스에서 삭제`}
                    onClick={() => removeCandidate(index)}
                  >
                    삭제
                  </Button>
                </div>
              </li>
            ))}
          </ol>
        )}

        <p className={styles.disclaimer}>
          첫 맛집을 출발점으로 자동차 이동 순서를 제안합니다. 현재 위치·영업시간·실시간 교통은
          사용하지 않습니다.
        </p>

        <div className={styles.calculateRow}>
          <Button
            type="button"
            disabled={calculateDisabled}
            aria-describedby={sizeGuidance ? 'course-size-guidance' : undefined}
            onClick={() => void calculateCourse()}
          >
            {calculating ? '계산 중…' : '코스 계산'}
          </Button>
          {sizeGuidance ? (
            <p id="course-size-guidance" className={styles.selectHint}>
              {sizeGuidance.message}
            </p>
          ) : null}
        </div>
      </section>

      {outcome ? (
        <section className={styles.resultPanel} aria-labelledby="course-outcome-heading">
          {outcome.kind === 'success' ? (
            <CourseResult
              route={outcome.route}
              expired={expired}
              onReselect={backToBuilder}
              onRefresh={() => void calculateCourse()}
              refreshing={calculating}
            />
          ) : (
            <CourseProblem
              outcome={outcome}
              onReselect={backToBuilder}
              onRetry={() => void calculateCourse()}
              retrying={calculating}
            />
          )}
        </section>
      ) : null}
    </div>
  )
}

function CourseResult({
  route,
  expired,
  onReselect,
  onRefresh,
  refreshing,
}: {
  route: Extract<CourseRouteOutcome, { kind: 'success' }>['route']
  expired: boolean
  onReselect: () => void
  onRefresh: () => void
  refreshing: boolean
}) {
  return (
    <div>
      <div className={styles.resultHeader}>
        <h2 id="course-outcome-heading">추천 이동 순서</h2>
        <p className={styles.resultTimestamps}>
          생성 {formatTimestamp(route.generatedAt)} · 만료 {formatTimestamp(route.expiresAt)}
        </p>
      </div>

      {expired ? (
        <p className={styles.expiredNotice} role="status">
          이 결과는 만료되어 거리·시간을 더 이상 최신으로 보여줄 수 없습니다. 새 경로를 다시
          조회해 주세요.
        </p>
      ) : null}

      <ol className={styles.resultList}>
        {route.restaurants.map((restaurant, index) => {
          const segment = route.segments[index]
          return (
            <li key={restaurant.restaurantId} className={styles.resultItem}>
              <span className={styles.selectionOrder}>{restaurant.sequence}</span>
              <div>
                <p className={styles.selectionName}>{restaurant.name}</p>
                {!expired && segment ? (
                  <p className={styles.segmentInfo}>
                    자동차 {formatCourseDistance(segment.distanceMeters)} ·{' '}
                    {formatCourseDuration(segment.durationSeconds)} (외부 경로 계산 결과이며 도착을
                    보장하지 않습니다)
                  </p>
                ) : null}
              </div>
            </li>
          )
        })}
      </ol>

      {!expired ? (
        <p className={styles.totalInfo}>
          전체 {formatCourseDistance(route.totalDistanceMeters)} ·{' '}
          {formatCourseDuration(route.totalDurationSeconds)}
        </p>
      ) : null}

      <div className={styles.outcomeActions}>
        <Button type="button" variant="secondary" onClick={onReselect}>
          선택 수정
        </Button>
        <Button type="button" disabled={refreshing} onClick={onRefresh}>
          {refreshing ? '조회 중…' : '새 경로 조회'}
        </Button>
      </div>
    </div>
  )
}

function CourseProblem({
  outcome,
  onReselect,
  onRetry,
  retrying,
}: {
  outcome: Exclude<CourseRouteOutcome, { kind: 'success' }>
  onReselect: () => void
  onRetry: () => void
  retrying: boolean
}) {
  const categoryLabel =
    outcome.kind === 'invalid'
      ? courseInvalidCategoryLabel(outcome.category)
      : outcome.kind === 'failure'
        ? courseFailureCategoryLabel(outcome.category)
        : '오류'

  /*
   * invalid(4xx)는 선택을 바꾸지 않는 한 같은 요청이 같은 오류를 반복하므로 다시 시도를
   * 보여주지 않는다. failure(5xx/429)만 서버가 허용을 밝혔거나 일시 오류일 때 다시 시도를 보여준다.
   */
  const showRetry = outcome.kind === 'error' || (outcome.kind === 'failure' && outcome.retryAllowed)

  return (
    <div role="alert">
      <h2 id="course-outcome-heading">경로를 만들 수 없습니다</h2>
      <p className={styles.problemBadge}>[{categoryLabel}]</p>
      <p>{outcome.message}</p>
      {outcome.traceId ? (
        <p className={styles.traceId}>traceId: {outcome.traceId}</p>
      ) : null}
      {outcome.kind === 'failure' && !outcome.retryAllowed ? (
        <p className={styles.selectHint} id="course-retry-guidance">
          지금은 다시 시도할 수 없습니다. 잠시 후 다시 방문해 주세요.
        </p>
      ) : null}
      <div className={styles.outcomeActions}>
        <Button type="button" variant="secondary" onClick={onReselect}>
          선택 수정
        </Button>
        {showRetry ? (
          <Button type="button" disabled={retrying} onClick={onRetry}>
            {retrying ? '다시 시도 중…' : '다시 시도'}
          </Button>
        ) : null}
        <Link href="/restaurants" className={styles.exploreLink}>
          기존 맛집 탐색
        </Link>
      </div>
    </div>
  )
}

function formatTimestamp(isoValue: string): string {
  const parsed = new Date(isoValue)
  if (Number.isNaN(parsed.getTime())) {
    return isoValue
  }
  return parsed.toLocaleString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    month: 'long',
    day: 'numeric',
  })
}
