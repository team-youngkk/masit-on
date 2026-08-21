'use client'

import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { FavoriteButton } from '@/components/personal/FavoriteButton'
import { Card } from '@/components/ui/Card'
import { StatePanel } from '@/components/ui/StatePanel'
import { StatusBadge } from '@/components/ui/StatusBadge'
import {
  NATURAL_LANGUAGE_EMPTY_MESSAGE,
  NATURAL_LANGUAGE_TAG_LIMIT,
  NATURAL_LANGUAGE_TAG_OPTIONS,
  formatNaturalLanguageAppliedConditions,
  isNaturalLanguageRetryAllowed,
  naturalLanguageConditionLabel,
  naturalLanguageFiltersFromFormData,
  naturalLanguageRetryDelay,
  searchRestaurantsByNaturalLanguage,
  toggleNaturalLanguageTag,
  type NaturalLanguageSearchFilters,
  type NaturalLanguageSearchOutcome,
} from '@/lib/natural-language-search-api'

import styles from './NaturalLanguageRestaurantSearch.module.css'

type Props = { filters: NaturalLanguageSearchFilters; returnTo: string; structuredFormId: string; creatorLabels: Record<string, string> }

const TAG_GROUPS = [
  { label: '음식 종류', prefix: 'MENU_' },
  { label: '맛', prefix: 'TASTE_' },
  { label: '상황', prefix: 'OCCASION_' },
  { label: '분위기', prefix: 'ATMOSPHERE_' },
] as const

export function NaturalLanguageRestaurantSearch({ filters, returnTo, structuredFormId, creatorLabels }: Props) {
  const [sentence, setSentence] = useState('')
  /* 여러 태그 AND는 목록 API(단일 `tag`)가 아니라 자연어 API의 filters.tags가 담당하므로
   * (restaurant-discovery-api.md 6절) 직접 태그 선택은 구조화 폼이 아니라 이 영역이 소유한다.
   * 선택지는 V4 seed의 확정 태그 상수라서 태그가 DEPRECATED되면 서버 400으로만 걸러진다. */
  const [tags, setTags] = useState<string[]>(filters.tags)
  const [outcome, setOutcome] = useState<NaturalLanguageSearchOutcome | null>(null)
  const [pending, setPending] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const [now, setNow] = useState(Date.now())
  const controller = useRef<AbortController | null>(null)
  const searchInputRef = useRef<HTMLInputElement | null>(null)
  const last = useRef({ sentence: '', page: 1, filters })

  function expandSearch() {
    setExpanded(true)
    window.requestAnimationFrame(() => searchInputRef.current?.focus())
  }

  function liveFilters() {
    const form = document.getElementById(structuredFormId)
    return form instanceof HTMLFormElement
      ? naturalLanguageFiltersFromFormData(new FormData(form), tags)
      : { ...filters, tags }
  }

  /* 구조화 필터와 같이 선택만 바꾸고 조회는 다음 문장 검색에서 한다. 토글마다 재조회하면
   * 429 Retry-After 대기와 편집 중인 문장을 무시한 요청이 나간다. */
  function toggleTag(code: string) {
    setTags(toggleNaturalLanguageTag(tags, code))
  }

  async function submit(value: string, page = 1, requestFilters = page === 1 ? liveFilters() : last.current.filters) {
    const trimmed = value.trim()
    if (!trimmed) { setOutcome({ kind: 'invalid', code: 'NATURAL_LANGUAGE_EMPTY', message: NATURAL_LANGUAGE_EMPTY_MESSAGE, fieldGuidance: [] }); return }
    controller.current?.abort()
    const next = new AbortController()
    controller.current = next
    last.current = { sentence: trimmed, page, filters: requestFilters }
    setPending(true)
    setOutcome(null)
    try {
      const nextOutcome = await searchRestaurantsByNaturalLanguage(trimmed, requestFilters, page, next.signal)
      if (controller.current === next) {
        setNow(Date.now())
        setOutcome(nextOutcome)
      }
    } catch {} finally { if (controller.current === next) setPending(false) }
  }

  const result = outcome?.kind === 'success' ? outcome.result : null
  const retryAvailableAt = outcome?.kind === 'rateLimited' ? outcome.retryAvailableAt : null
  const retryBlocked = retryAvailableAt !== null && now < retryAvailableAt
  useEffect(() => {
    if (!retryBlocked || retryAvailableAt === null) return
    const timer = window.setTimeout(() => setNow(Date.now()), naturalLanguageRetryDelay(retryAvailableAt, now))
    return () => window.clearTimeout(timer)
  }, [now, retryAvailableAt, retryBlocked])
  useEffect(() => () => {
    const active = controller.current
    active?.abort()
    if (controller.current === active) controller.current = null
  }, [])
  const applied = result ? formatNaturalLanguageAppliedConditions(result.interpretation.appliedConditions, creatorLabels) : []
  const selectedConditions = [
    filters.query ? `“${filters.query}”` : null,
    filters.district,
    filters.category,
    filters.creatorId ? creatorLabels[filters.creatorId] ?? '선택한 유튜버' : null,
    ...tags.map((tag) => NATURAL_LANGUAGE_TAG_OPTIONS.find((option) => option.code === tag)?.label ?? tag),
  ].filter((condition): condition is string => Boolean(condition))

  if (!expanded) {
    return <section id="natural-language-search-panel" className={styles.collapsedSearch} aria-label="원하는 맛집 찾기">
      <button
        type="button"
        className={styles.collapsedButton}
        aria-expanded={false}
        aria-controls="natural-language-search-panel"
        onClick={expandSearch}
      >
        <svg
          className={styles.collapsedIcon}
          viewBox="0 0 32 32"
          fill="none"
          aria-hidden="true"
        >
          <circle cx="13.5" cy="13.5" r="8.5" stroke="currentColor" strokeWidth="2.25" />
          <path d="M20 20L27.5 27.5" stroke="currentColor" strokeWidth="2.25" strokeLinecap="round" />
        </svg>
        <span className={styles.collapsedCopy}>
          <strong>원하는 맛집이 보이지 않나요?</strong>
          <span>검색어나 필터를 변경해보거나, 다른 지역을 선택해보세요.</span>
        </span>
        <span className={styles.collapsedArrow} aria-hidden="true">→</span>
      </button>
    </section>
  }

  return <section id="natural-language-search-panel" className={styles.search} aria-label="원하는 맛집 찾기">
    <div className={styles.headingRow}>
      <div>
        <h2 className={styles.title}>찾고 싶은 맛집을 알려주세요</h2>
        <p className={styles.description}>지역, 메뉴, 분위기를 편하게 적어보세요.</p>
      </div>
      <button
        type="button"
        className={styles.collapseButton}
        aria-expanded={true}
        aria-controls="natural-language-search-panel"
        onClick={() => setExpanded(false)}
      >
        접기
      </button>
    </div>
    <div className={styles.searchGrid}>
      <form className={styles.form} onSubmit={(event) => { event.preventDefault(); void submit(sentence) }}>
        <label htmlFor="natural-language-sentence" className={styles.label}>원하는 조건</label>
        <div className={styles.controls}>
          <input ref={searchInputRef} id="natural-language-sentence" className={styles.input} value={sentence} onChange={(event) => setSentence(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.nativeEvent.isComposing) { event.preventDefault(); void submit(sentence) } }} maxLength={500} required disabled={pending} placeholder="예: 성동구에서 냉면 먹기 좋은 곳" />
          <Button type="submit" disabled={pending}>{pending ? '찾는 중…' : '찾아보기'}</Button>
        </div>
        <fieldset className={styles.tags} disabled={pending}>
          <legend className={styles.label}>추천 조건 ({tags.length}/{NATURAL_LANGUAGE_TAG_LIMIT})</legend>
          <p className={styles.tagHint}>조건을 골라두면 입력한 내용과 함께 검색해요.</p>
          {TAG_GROUPS.map((group) => <div key={group.prefix} className={styles.tagGroup}>
            <h3 className={styles.tagGroupTitle}>{group.label}</h3>
            <div className={styles.tagOptions}>
              {NATURAL_LANGUAGE_TAG_OPTIONS.filter((option) => option.code.startsWith(group.prefix)).map((option) => <label key={option.code} className={`${styles.tagOption} ${tags.includes(option.code) ? styles.tagSelected : ''}`}>
                <input type="checkbox" checked={tags.includes(option.code)} disabled={!tags.includes(option.code) && tags.length >= NATURAL_LANGUAGE_TAG_LIMIT} onChange={() => toggleTag(option.code)} />
                {option.label}
              </label>)}
            </div>
          </div>)}
        </fieldset>
      </form>
      <aside className={styles.criteriaPanel} aria-label="현재 검색 조건">
        <div className={styles.criteriaHeading}>
          <div>
            <strong>이렇게 이해했어요</strong>
            <p>선택한 조건을 함께 적용합니다.</p>
          </div>
        </div>
        <div className={styles.criteriaChips}>
          {selectedConditions.length ? selectedConditions.map((condition) => <span key={condition}>{condition}</span>) : <span className={styles.emptyCriteria}>조건을 입력하면 여기에 표시됩니다.</span>}
        </div>
      </aside>
    </div>
    <div className={styles.status} aria-live="polite" aria-atomic="true">
      {pending ? <p>입력한 조건을 확인하고 있습니다.</p> : null}
      {result ? <>
        <div className={styles.summary}>
          <p><strong>{result.interpretation.status === 'FAILED' ? '해석 실패' : result.interpretation.status === 'PARTIAL' ? '일부 조건 적용' : '조건 적용 완료'}</strong></p>
          {applied.length ? <div className={styles.applied}>{applied.map((condition) => <StatusBadge key={condition} tone="success" className={styles.appliedBadge}>{condition}</StatusBadge>)}</div> : null}
          <p>총 {result.results.page.totalElements}건</p>
          {result.interpretation.ignoredConditions.length ? <p>적용하지 않은 조건: {result.interpretation.ignoredConditions.map((item) => item.text).join(', ')}</p> : null}
          {result.interpretation.conflicts.length ? <p>직접 필터 우선: {result.interpretation.conflicts.map((item) => naturalLanguageConditionLabel(item.field.toLowerCase())).join(', ')}</p> : null}
        </div>
        {result.interpretation.status === 'FAILED' ? <StatePanel compact tone="warning" title="문장에서 적용할 조건을 찾지 못했습니다" description={<>문장을 수정하거나 <a href="#structured-restaurant-search">기존 필터 검색</a>을 이용해 보세요.</>} /> : result.results.items.length === 0 ? <StatePanel compact title="적용한 조건과 일치하는 맛집이 없습니다" description={<>문장·직접 필터·태그를 바꾸거나 <a href="#structured-restaurant-search">기존 필터 검색</a>을 이용해 보세요.</>} /> : <>
          <ul className={styles.results}>{result.results.items.map((restaurant) => <li key={restaurant.id}><Card title={<Link href={`/restaurants/${encodeURIComponent(restaurant.id)}`}>{restaurant.name}</Link>} level={3} meta={`${restaurant.district} · ${restaurant.category}`}><FavoriteButton restaurantId={restaurant.id} restaurantName={restaurant.name} returnTo={returnTo} />{restaurant.visitedBy.length ? <p className={styles.visitedBy}>방문 유튜버 {restaurant.visitedBy.map((creator) => creator.channelName).join(', ')}{restaurant.remainingVisitedByCount > 0 ? ` 외 ${restaurant.remainingVisitedByCount}명` : ''}</p> : null}</Card></li>)}</ul>
          {result.results.page.totalPages > 1 ? <nav className={styles.pagination} aria-label="자연어 검색 결과 페이지"><Button type="button" variant="secondary" disabled={pending || result.results.page.number <= 1} onClick={() => void submit(last.current.sentence, result.results.page.number - 1, last.current.filters)}>이전</Button><span>{result.results.page.number} / {result.results.page.totalPages}</span><Button type="button" variant="secondary" disabled={pending || !result.results.page.hasNext} onClick={() => void submit(last.current.sentence, result.results.page.number + 1, last.current.filters)}>다음</Button></nav> : null}
        </>}
      </> : null}
      {outcome && outcome.kind !== 'success' ? <StatePanel tone="danger" title="검색 조건을 확인해 주세요" description={<><p>{outcome.message}</p>{outcome.kind === 'invalid' && outcome.fieldGuidance.length ? <ul className={styles.guidance}>{outcome.fieldGuidance.map((item, index) => <li key={`${index}-${item.label}`}>{item.label}: {item.reason}</li>)}</ul> : null}<p><a href="#structured-restaurant-search">기존 필터 검색으로 이동</a></p></>} traceId={outcome.traceId} actions={isNaturalLanguageRetryAllowed(outcome) ? <Button type="button" variant="secondary" onClick={() => void submit(last.current.sentence, last.current.page, last.current.filters)} disabled={pending || retryBlocked}>{retryBlocked ? '잠시 후 다시 시도' : '다시 시도'}</Button> : null} /> : null}
    </div>
  </section>
}
