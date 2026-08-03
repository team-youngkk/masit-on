'use client'

import { useEffect, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import {
  CATEGORY_OPTIONS,
  DISTRICT_OPTIONS,
  type FetchCreatorsResult,
} from '@/lib/restaurants-api'
import type { MapPointsFilters } from '@/lib/map/map-points-query'

import styles from './MapFilterForm.module.css'

type MapFilterFormProps = {
  initialFilters: MapPointsFilters
  creatorsResult: FetchCreatorsResult
  onApply: (filters: MapPointsFilters) => void
  onReset: () => void
}

/*
 * /restaurants의 필터 필드를 그대로 미러링하되, 지도 화면은 method=get 전체 페이지 이동 대신
 * 제출·초기화 시 부모가 URL을 얕게 갱신하도록 콜백을 호출한다(지도 viewport 보존).
 */
export function MapFilterForm({
  initialFilters,
  creatorsResult,
  onApply,
  onReset,
}: MapFilterFormProps) {
  const [draft, setDraft] = useState<MapPointsFilters>(initialFilters)

  /*
   * initialFilters는 URL 검색 상태다. 사용자가 타이핑하는 동안에는 손대지 않지만,
   * 브라우저 뒤로·앞으로 가기처럼 이 화면 밖에서 URL이 바뀌면 draft도 다시 맞춘다.
   */
  useEffect(() => {
    setDraft(initialFilters)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    initialFilters.query,
    initialFilters.district,
    initialFilters.category,
    initialFilters.creatorId,
  ])

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onApply({
      query: draft.query?.trim() || undefined,
      district: draft.district || undefined,
      category: draft.category || undefined,
      creatorId: draft.creatorId || undefined,
    })
  }

  function handleReset() {
    setDraft({})
    onReset()
  }

  return (
    <form onSubmit={handleSubmit} className={styles.filters} aria-label="지도 검색 조건">
      <Field
        label="맛집 이름"
        name="query"
        value={draft.query ?? ''}
        onChange={(event) =>
          setDraft((current) => ({ ...current, query: event.target.value }))
        }
        placeholder="예: 강된장"
        maxLength={100}
      />

      <div className={styles.selectGroup}>
        <label className={styles.selectLabel} htmlFor="map-district">
          자치구
        </label>
        <select
          id="map-district"
          className={styles.select}
          value={draft.district ?? ''}
          onChange={(event) =>
            setDraft((current) => ({
              ...current,
              district: event.target.value || undefined,
            }))
          }
        >
          <option value="">전체</option>
          {DISTRICT_OPTIONS.map((district) => (
            <option key={district} value={district}>
              {district}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.selectGroup}>
        <label className={styles.selectLabel} htmlFor="map-category">
          대표 음식
        </label>
        <select
          id="map-category"
          className={styles.select}
          value={draft.category ?? ''}
          onChange={(event) =>
            setDraft((current) => ({
              ...current,
              category: event.target.value || undefined,
            }))
          }
        >
          <option value="">전체</option>
          {CATEGORY_OPTIONS.map((category) => (
            <option key={category} value={category}>
              {category}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.selectGroup}>
        <label className={styles.selectLabel} htmlFor="map-creator">
          유튜버
        </label>
        {creatorsResult.ok ? (
          <select
            id="map-creator"
            className={styles.select}
            value={draft.creatorId ?? ''}
            onChange={(event) =>
              setDraft((current) => ({
                ...current,
                creatorId: event.target.value || undefined,
              }))
            }
          >
            <option value="">전체</option>
            {creatorsResult.data.items.map((creator) => (
              <option key={creator.id} value={creator.id}>
                {creator.channelName}
              </option>
            ))}
          </select>
        ) : (
          <p className={styles.selectError} role="alert">
            {creatorsResult.message}
            {creatorsResult.traceId ? (
              <span className={styles.traceId}>traceId: {creatorsResult.traceId}</span>
            ) : null}
          </p>
        )}
      </div>

      <div className={styles.actions}>
        <Button type="submit">검색</Button>
        <Button type="button" variant="secondary" onClick={handleReset}>
          조건 초기화
        </Button>
      </div>
    </form>
  )
}
