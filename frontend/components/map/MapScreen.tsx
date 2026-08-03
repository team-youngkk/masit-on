'use client'

import { useRouter } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import { fetchMapPoints, type MapPointsFetchResult } from '@/lib/map/map-points-client'
import {
  SEOUL_FALLBACK_BOUNDS,
  buildMapPointsQueryKey,
  type MapPointsFilters,
} from '@/lib/map/map-points-query'
import type { MapPointsViewState } from '@/lib/map/map-points-response'
import { findSelectedMapPoint, toggleMapSelection } from '@/lib/map/selection-sync'
import type { FetchCreatorsResult } from '@/lib/restaurants-api'

import { KakaoMapView } from './KakaoMapView'
import { MapFilterForm } from './MapFilterForm'
import { MapResultList } from './MapResultList'
import { MapSelectionSummary } from './MapSelectionSummary'

import styles from './MapScreen.module.css'

type MapScreenProps = {
  initialFilters: MapPointsFilters
  creatorsResult: FetchCreatorsResult
}

type Banner = {
  kind: 'invalid' | 'rateLimited' | 'error'
  message: string
  traceId?: string
}

function buildMapHref(filters: MapPointsFilters): string {
  const params = new URLSearchParams()
  if (filters.query) params.set('query', filters.query)
  if (filters.district) params.set('district', filters.district)
  if (filters.category) params.set('category', filters.category)
  if (filters.creatorId) params.set('creatorId', filters.creatorId)
  const queryString = params.toString()
  return queryString ? `/map?${queryString}` : '/map'
}

/* useQuery의 data 하나로부터 배너 상태를 계산한다. 최초 렌더 파생과 이후 갱신 양쪽에서 쓴다. */
function deriveBanner(data: MapPointsFetchResult | undefined): Banner | null {
  if (!data || data.kind === 'ok') {
    return null
  }
  if (data.kind === 'rateLimited') {
    return { kind: 'rateLimited', message: data.message, traceId: data.traceId }
  }
  return { kind: data.kind, message: data.message, traceId: data.traceId }
}

/*
 * ADR-WEB-002: 필터 네 조건만 URL 쿼리로 공유 가능하게 유지한다. 지도 뷰포트는 서버 조회·
 * URL·로그와 무관한 Kakao 지도 전용 표시 상태이며 이 컴포넌트의 state로 두지 않는다
 * (ADR-MAP-001 4.2~4.3).
 */
export function MapScreen({ initialFilters, creatorsResult }: MapScreenProps) {
  const router = useRouter()
  const queryClient = useQueryClient()

  /*
   * 필터는 URL 검색 상태이므로 로컬 state로 복제하지 않는다(ADR-WEB-002 11절).
   * page.tsx가 searchParams를 다시 읽어 넘기는 이 prop을 그대로 querying 기준으로 쓰면
   * 브라우저 뒤로·앞으로 가기로 바뀐 URL에도 자동으로 다시 반응한다.
   */
  const filters = initialFilters
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [filtersOpen, setFiltersOpen] = useState(false)
  /*
   * hydrated 결과가 우연히 rateLimited였는지는 useQuery를 호출하기 전에는 알 수 없어
   * (아래 enabled 계산과 순환 참조가 생긴다) 초기값은 null로 둔다. 실제로 그 상태였다면
   * useQuery가 즉시 재조회해 서버가 다시 429를 반환하는 순간 정상적으로 rateLimitedUntil이
   * 설정되어 이후 호출을 막는다.
   */
  const [rateLimitedUntil, setRateLimitedUntil] = useState<number | null>(null)

  const queryKey = useMemo(
    () => buildMapPointsQueryKey(filters),
    [filters.category, filters.creatorId, filters.district, filters.query],
  )

  const isRateLimited = rateLimitedUntil !== null && Date.now() < rateLimitedUntil

  const { data } = useQuery({
    queryKey,
    queryFn: ({ signal }) => fetchMapPoints(filters, signal),
    enabled: !isRateLimited,
  })

  /*
   * page.tsx가 서버에서 미리 조회해 hydrate한 결과를 최초 렌더부터 그대로 보여줘야 한다
   * (ADR-WEB-002). useEffect로 data를 lastGoodView/banner에 복사하면 effect는 서버 렌더·
   * hydration 시점에 실행되지 않아 최초 HTML과 첫 페인트가 항상 로딩 상태로 나온다. 대신
   * "렌더링 중 이전 값 갱신" 패턴(react.dev의 useState 문서가 권장하는 방식)을 써서, data가
   * 바뀔 때마다 effect를 기다리지 않고 같은 렌더에서 즉시 파생시킨다.
   */
  const [lastGoodView, setLastGoodView] = useState<MapPointsViewState | null>(
    () => (data?.kind === 'ok' ? data.view : null),
  )
  const [banner, setBanner] = useState<Banner | null>(() => deriveBanner(data))

  const [previousData, setPreviousData] = useState(data)
  if (data !== previousData) {
    setPreviousData(data)
    if (data?.kind === 'ok') {
      setLastGoodView(data.view)
      setBanner(null)
      setRateLimitedUntil(null)
    } else if (data?.kind === 'rateLimited') {
      setBanner({ kind: 'rateLimited', message: data.message, traceId: data.traceId })
      setRateLimitedUntil(data.retryAvailableAt)
    } else if (data) {
      setBanner({ kind: data.kind, message: data.message, traceId: data.traceId })
    }
  }

  useEffect(() => {
    if (rateLimitedUntil === null) {
      return
    }

    const delay = rateLimitedUntil - Date.now()
    if (delay <= 0) {
      setRateLimitedUntil(null)
      return
    }

    const timer = setTimeout(() => {
      setRateLimitedUntil(null)
      void queryClient.invalidateQueries({ queryKey })
    }, delay)

    return () => clearTimeout(timer)
  }, [queryClient, queryKey, rateLimitedUntil])

  /* 필터가 실제로 바뀌면(제출·초기화·뒤로가기 모두 포함) 이전 선택은 더 이상 유효하지 않다. */
  useEffect(() => {
    setSelectedId(null)
  }, [filters.query, filters.district, filters.category, filters.creatorId])

  /* 필터 제출·초기화는 사용자가 의도한 탐색 지점이므로 history에 새 항목을 남긴다(뒤로 가기로
   * 이전 필터 조합에 돌아올 수 있어야 한다). 지도 bounds는 이 경로와 무관하게 URL에 두지 않는다. */
  function applyFilters(next: MapPointsFilters) {
    router.push(buildMapHref(next), { scroll: false })
  }

  const items = lastGoodView?.kind === 'results' ? lastGoodView.items : []
  const selectedItem = findSelectedMapPoint(items, selectedId)
  const isInitialLoading = lastGoodView === null && banner === null
  const isInitialBlocked = lastGoodView === null && banner !== null

  function handleSelect(id: string) {
    setSelectedId((current) => toggleMapSelection(current, id))
  }

  return (
    <section className={styles.screen}>
      <h1>지도 탐색</h1>

      <button
        type="button"
        className={styles.filterToggle}
        aria-expanded={filtersOpen}
        onClick={() => setFiltersOpen((open) => !open)}
      >
        검색·필터 {filtersOpen ? '닫기' : '열기'}
      </button>

      <div
        className={
          filtersOpen ? `${styles.filterContent} ${styles.filterContentOpen}` : styles.filterContent
        }
      >
        <MapFilterForm
          initialFilters={filters}
          creatorsResult={creatorsResult}
          onApply={applyFilters}
          onReset={() => applyFilters({})}
        />
      </div>

      {banner ? (
        <p className={banner.kind === 'error' ? styles.error : styles.notice} role="alert">
          {banner.message}
          {banner.traceId ? <span className={styles.traceId}>traceId: {banner.traceId}</span> : null}
        </p>
      ) : null}

      <div className={styles.layout}>
        <div className={styles.mapArea}>
          <KakaoMapView
            items={items}
            selectedId={selectedId}
            fallbackBounds={SEOUL_FALLBACK_BOUNDS}
            onSelect={handleSelect}
          />
        </div>

        <div className={styles.summaryArea}>
          <MapSelectionSummary selected={selectedItem} />
        </div>

        <div className={styles.listArea}>
          <MapResultList
            view={lastGoodView}
            isLoading={isInitialLoading}
            isBlocked={isInitialBlocked}
            selectedId={selectedId}
            onSelect={handleSelect}
            onResetFilters={() => applyFilters({})}
          />
        </div>
      </div>
    </section>
  )
}
