'use client'

import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { FavoriteButton } from '@/components/personal/FavoriteButton'
import { Card } from '@/components/ui/Card'
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

export function NaturalLanguageRestaurantSearch({ filters, returnTo, structuredFormId, creatorLabels }: Props) {
  const [sentence, setSentence] = useState('')
  /* 여러 태그 AND는 목록 API(단일 `tag`)가 아니라 자연어 API의 filters.tags가 담당하므로
   * (restaurant-discovery-api.md 6절) 직접 태그 선택은 구조화 폼이 아니라 이 영역이 소유한다.
   * 선택지는 V4 seed의 확정 태그 상수라서 태그가 DEPRECATED되면 서버 400으로만 걸러진다. */
  const [tags, setTags] = useState<string[]>(filters.tags)
  const [outcome, setOutcome] = useState<NaturalLanguageSearchOutcome | null>(null)
  const [pending, setPending] = useState(false)
  const [now, setNow] = useState(Date.now())
  const controller = useRef<AbortController | null>(null)
  const last = useRef({ sentence: '', page: 1, filters })

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

  return <section className={styles.search} aria-label="문장으로 맛집 찾기">
    <h2 className={styles.title}>문장으로 맛집 찾기</h2>
    <p className={styles.description}>예: 성동구에서 냉면 먹기 좋은 곳. 선택된 기존 필터는 함께 적용됩니다.</p>
    <form className={styles.form} onSubmit={(event) => { event.preventDefault(); void submit(sentence) }}>
      <label htmlFor="natural-language-sentence" className={styles.label}>자연어 검색 문장</label>
      <div className={styles.controls}>
        <input id="natural-language-sentence" className={styles.input} value={sentence} onChange={(event) => setSentence(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.nativeEvent.isComposing) { event.preventDefault(); void submit(sentence) } }} maxLength={500} required disabled={pending} placeholder="원하는 맛집 조건을 문장으로 입력하세요" />
        <Button type="submit" disabled={pending}>{pending ? '해석 중…' : '문장 검색'}</Button>
      </div>
      <fieldset className={styles.tags} disabled={pending}>
        <legend className={styles.label}>직접 지정 태그 ({tags.length}/{NATURAL_LANGUAGE_TAG_LIMIT} 선택)</legend>
        <p className={styles.tagHint}>선택한 태그는 다음 문장 검색에 적용되고, 문장에서 해석한 태그를 대체합니다.</p>
        {NATURAL_LANGUAGE_TAG_OPTIONS.map((option) => <label key={option.code} className={styles.tagOption}>
          <input type="checkbox" checked={tags.includes(option.code)} disabled={!tags.includes(option.code) && tags.length >= NATURAL_LANGUAGE_TAG_LIMIT} onChange={() => toggleTag(option.code)} />
          {option.label}
        </label>)}
      </fieldset>
    </form>
    <div className={styles.status} aria-live="polite" aria-atomic="true">
      {pending ? <p>입력한 조건을 확인하고 있습니다.</p> : null}
      {result ? <>
        <div className={styles.summary}>
          <p><strong>{result.interpretation.status === 'FAILED' ? '해석 실패' : result.interpretation.status === 'PARTIAL' ? '일부 조건 적용' : '조건 적용 완료'}</strong>{applied.length ? ` · ${applied.join(' / ')}` : ''}</p>
          <p>총 {result.results.page.totalElements}건</p>
          {result.interpretation.ignoredConditions.length ? <p>적용하지 않은 조건: {result.interpretation.ignoredConditions.map((item) => item.text).join(', ')}</p> : null}
          {result.interpretation.conflicts.length ? <p>직접 필터 우선: {result.interpretation.conflicts.map((item) => naturalLanguageConditionLabel(item.field.toLowerCase())).join(', ')}</p> : null}
        </div>
        {result.interpretation.status === 'FAILED' ? <p className={styles.failure}>문장에서 적용할 조건을 찾지 못했습니다. <a href="#structured-restaurant-search">기존 필터 검색으로 이동</a></p> : result.results.items.length === 0 ? <p className={styles.empty}>적용한 조건과 일치하는 맛집이 없습니다. 조건을 바꾸거나 <a href="#structured-restaurant-search">기존 필터 검색으로 이동</a>해 보세요.</p> : <>
          <ul className={styles.results}>{result.results.items.map((restaurant) => <li key={restaurant.id}><Card title={<Link href={`/restaurants/${encodeURIComponent(restaurant.id)}`}>{restaurant.name}</Link>} level={3} meta={`${restaurant.district} · ${restaurant.category}`}><FavoriteButton restaurantId={restaurant.id} restaurantName={restaurant.name} returnTo={returnTo} />{restaurant.visitedBy.length ? <p className={styles.visitedBy}>방문 유튜버 {restaurant.visitedBy.map((creator) => creator.channelName).join(', ')}{restaurant.remainingVisitedByCount > 0 ? ` 외 ${restaurant.remainingVisitedByCount}명` : ''}</p> : null}</Card></li>)}</ul>
          {result.results.page.totalPages > 1 ? <nav className={styles.pagination} aria-label="자연어 검색 결과 페이지"><Button type="button" variant="secondary" disabled={pending || result.results.page.number <= 1} onClick={() => void submit(last.current.sentence, result.results.page.number - 1, last.current.filters)}>이전</Button><span>{result.results.page.number} / {result.results.page.totalPages}</span><Button type="button" variant="secondary" disabled={pending || !result.results.page.hasNext} onClick={() => void submit(last.current.sentence, result.results.page.number + 1, last.current.filters)}>다음</Button></nav> : null}
        </>}
      </> : null}
      {outcome && outcome.kind !== 'success' ? <div className={styles.failure} role="alert"><p>{outcome.message}</p>{outcome.kind === 'invalid' && outcome.fieldGuidance.length ? <ul className={styles.guidance}>{outcome.fieldGuidance.map((item, index) => <li key={`${index}-${item.label}`}>{item.label}: {item.reason}</li>)}</ul> : null}{outcome.traceId ? <p className={styles.traceId}>traceId: {outcome.traceId}</p> : null}{isNaturalLanguageRetryAllowed(outcome) ? <Button type="button" variant="secondary" onClick={() => void submit(last.current.sentence, last.current.page, last.current.filters)} disabled={pending || retryBlocked}>{retryBlocked ? '잠시 후 다시 시도' : '다시 시도'}</Button> : null}<p><a href="#structured-restaurant-search">기존 필터 검색으로 이동</a></p></div> : null}
    </div>
  </section>
}
