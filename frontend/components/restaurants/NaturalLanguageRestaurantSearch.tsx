'use client'

import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { FavoriteButton } from '@/components/personal/FavoriteButton'
import { Card } from '@/components/ui/Card'
import {
  isNaturalLanguageRetryAllowed,
  naturalLanguageFiltersFromFormData,
  searchRestaurantsByNaturalLanguage,
  type NaturalLanguageSearchFilters,
  type NaturalLanguageSearchOutcome,
} from '@/lib/natural-language-search-api'

import styles from './NaturalLanguageRestaurantSearch.module.css'

type Props = { filters: NaturalLanguageSearchFilters; returnTo: string; structuredFormId: string }
const labels: Record<string, string> = { query: '이름', district: '자치구', category: '음식 종류', creatorId: '유튜버', tags: '태그' }

export function NaturalLanguageRestaurantSearch({ filters, returnTo, structuredFormId }: Props) {
  const [sentence, setSentence] = useState('')
  const [outcome, setOutcome] = useState<NaturalLanguageSearchOutcome | null>(null)
  const [pending, setPending] = useState(false)
  const [now, setNow] = useState(Date.now())
  const controller = useRef<AbortController | null>(null)
  const last = useRef({ sentence: '', page: 1, filters })

  function liveFilters() {
    const form = document.getElementById(structuredFormId)
    return form instanceof HTMLFormElement
      ? naturalLanguageFiltersFromFormData(new FormData(form), filters.tags)
      : filters
  }

  async function submit(value: string, page = 1, requestFilters = page === 1 ? liveFilters() : last.current.filters) {
    const trimmed = value.trim()
    if (!trimmed) { setOutcome({ kind: 'invalid', message: '자연어 검색 문장을 입력해 주세요.' }); return }
    controller.current?.abort()
    const next = new AbortController()
    controller.current = next
    last.current = { sentence: trimmed, page, filters: requestFilters }
    setNow(Date.now())
    setPending(true)
    setOutcome(null)
    try {
      const nextOutcome = await searchRestaurantsByNaturalLanguage(trimmed, requestFilters, page, next.signal)
      if (controller.current === next) setOutcome(nextOutcome)
    } catch {} finally { if (controller.current === next) setPending(false) }
  }

  const result = outcome?.kind === 'success' ? outcome.result : null
  const retryAvailableAt = outcome?.kind === 'rateLimited' ? outcome.retryAvailableAt : null
  const retryBlocked = retryAvailableAt !== null && now < retryAvailableAt
  useEffect(() => {
    if (!retryBlocked || retryAvailableAt === null) return
    const timer = window.setTimeout(() => setNow(Date.now()), retryAvailableAt - now)
    return () => window.clearTimeout(timer)
  }, [now, retryAvailableAt, retryBlocked])
  useEffect(() => () => {
    const active = controller.current
    active?.abort()
    if (controller.current === active) controller.current = null
  }, [])
  const applied = result ? Object.entries(result.interpretation.appliedConditions).flatMap(([field, value]) => Array.isArray(value) ? value.length ? [`${labels[field] ?? field}: ${value.join(', ')}`] : [] : value ? [`${labels[field] ?? field}: ${value}`] : []) : []

  return <section className={styles.search} aria-label="문장으로 맛집 찾기">
    <h2 className={styles.title}>문장으로 맛집 찾기</h2>
    <p className={styles.description}>예: 성동구에서 냉면 먹기 좋은 곳. 선택된 기존 필터는 함께 적용됩니다.</p>
    <form className={styles.form} onSubmit={(event) => { event.preventDefault(); void submit(sentence) }}>
      <label htmlFor="natural-language-sentence" className={styles.label}>자연어 검색 문장</label>
      <div className={styles.controls}>
        <input id="natural-language-sentence" className={styles.input} value={sentence} onChange={(event) => setSentence(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.nativeEvent.isComposing) { event.preventDefault(); void submit(sentence) } }} maxLength={500} required disabled={pending} placeholder="원하는 맛집 조건을 문장으로 입력하세요" />
        <Button type="submit" disabled={pending}>{pending ? '해석 중…' : '문장 검색'}</Button>
      </div>
    </form>
    <div className={styles.status} aria-live="polite" aria-atomic="true">
      {pending ? <p>입력한 조건을 확인하고 있습니다.</p> : null}
      {result ? <>
        <div className={styles.summary}>
          <p><strong>{result.interpretation.status === 'FAILED' ? '해석 실패' : result.interpretation.status === 'PARTIAL' ? '일부 조건 적용' : '조건 적용 완료'}</strong>{applied.length ? ` · ${applied.join(' / ')}` : ''}</p>
          <p>총 {result.results.page.totalElements}건</p>
          {result.interpretation.ignoredConditions.length ? <p>적용하지 않은 조건: {result.interpretation.ignoredConditions.map((item) => item.text).join(', ')}</p> : null}
          {result.interpretation.conflicts.length ? <p>직접 필터 우선: {result.interpretation.conflicts.map((item) => labels[item.field] ?? item.field).join(', ')}</p> : null}
        </div>
        {result.interpretation.status === 'FAILED' ? <p className={styles.failure}>문장에서 적용할 조건을 찾지 못했습니다. <a href="#structured-restaurant-search">기존 필터 검색으로 이동</a></p> : result.results.items.length === 0 ? <p className={styles.empty}>적용한 조건과 일치하는 맛집이 없습니다. 조건을 바꾸거나 <a href="#structured-restaurant-search">기존 필터 검색으로 이동</a>해 보세요.</p> : <>
          <ul className={styles.results}>{result.results.items.map((restaurant) => <li key={restaurant.id}><Card title={<Link href={`/restaurants/${encodeURIComponent(restaurant.id)}`}>{restaurant.name}</Link>} level={3} meta={`${restaurant.district} · ${restaurant.category}`}><FavoriteButton restaurantId={restaurant.id} restaurantName={restaurant.name} returnTo={returnTo} />{restaurant.visitedBy.length ? <p className={styles.visitedBy}>방문 유튜버 {restaurant.visitedBy.map((creator) => creator.channelName).join(', ')}{restaurant.remainingVisitedByCount > 0 ? ` 외 ${restaurant.remainingVisitedByCount}명` : ''}</p> : null}</Card></li>)}</ul>
          {result.results.page.totalPages > 1 ? <nav className={styles.pagination} aria-label="자연어 검색 결과 페이지"><Button type="button" variant="secondary" disabled={pending || result.results.page.number <= 1} onClick={() => void submit(last.current.sentence, result.results.page.number - 1, last.current.filters)}>이전</Button><span>{result.results.page.number} / {result.results.page.totalPages}</span><Button type="button" variant="secondary" disabled={pending || !result.results.page.hasNext} onClick={() => void submit(last.current.sentence, result.results.page.number + 1, last.current.filters)}>다음</Button></nav> : null}
        </>}
      </> : null}
      {outcome && outcome.kind !== 'success' ? <div className={styles.failure} role="alert"><p>{outcome.message}</p>{outcome.traceId ? <p className={styles.traceId}>traceId: {outcome.traceId}</p> : null}{isNaturalLanguageRetryAllowed(outcome) ? <Button type="button" variant="secondary" onClick={() => void submit(last.current.sentence, last.current.page, last.current.filters)} disabled={pending || retryBlocked}>{retryBlocked ? '잠시 후 다시 시도' : '다시 시도'}</Button> : null}<p><a href="#structured-restaurant-search">기존 필터 검색으로 이동</a></p></div> : null}
    </div>
  </section>
}
