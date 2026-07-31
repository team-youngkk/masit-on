'use client'

import Link from 'next/link'
import { useCallback, useState } from 'react'

import { Card } from '@/components/ui/Card'
import type { CreatorRestaurantsResponse, FetchCreatorRestaurantsResult } from '@/lib/creators-api'
import {
  loadCreatorListPage,
  nextCreatorListSearch,
} from '@/lib/creators/creator-list-navigation'

import { CreatorPageNav } from './CreatorPageNav'
import styles from './page.module.css'

const FALLBACK_MESSAGE =
  '방문 맛집을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

/*
 * API-CREATOR-DETAIL-002 방문 맛집 영역. 최초 데이터는 Server Component가 넘겨주고
 * (ADR-WEB-002) 이후 페이지 이동·재시도는 이 경계 안에서만 조회한다. 근거 영상 영역과
 * 상태를 공유하지 않으므로 한쪽 이동이 다른 목록을 재요청하지 않는다
 * (creator-detail-api.md 2절, PRD-DETAIL-002 5.2절).
 */
export function CreatorRestaurantsSection({
  creatorId,
  initialPage,
  initialResult,
}: {
  creatorId: string
  initialPage: number
  initialResult: FetchCreatorRestaurantsResult
}) {
  const [page, setPage] = useState(initialPage)
  const [result, setResult] = useState(initialResult)
  const [pending, setPending] = useState(false)

  const load = useCallback(
    async (nextPage: number) => {
      setPending(true)
      const loaded = await loadCreatorListPage<CreatorRestaurantsResponse>(
        creatorId,
        'restaurants',
        nextPage,
        FALLBACK_MESSAGE,
      )
      setResult(loaded)
      setPage(nextPage)
      setPending(false)

      /*
       * 새로고침·공유 시 페이지를 유지하되 서버를 다시 실행시키지 않는다. router 이동은
       * Server Component를 재실행해 상대 목록까지 재요청한다.
       */
      const search = nextCreatorListSearch(
        window.location.search,
        'restaurants',
        nextPage,
      )
      window.history.replaceState(null, '', `${window.location.pathname}?${search}`)
    },
    [creatorId],
  )

  if (!result.ok) {
    return (
      <div className={styles.sectionError} role="alert">
        <p>{result.message}</p>
        {result.traceId ? (
          <p className={styles.traceId}>traceId: {result.traceId}</p>
        ) : null}
        <button
          type="button"
          className={styles.retryLink}
          disabled={pending}
          onClick={() => void load(page)}
        >
          다시 시도
        </button>
      </div>
    )
  }

  const { items, page: pageInfo } = result.data

  if (items.length === 0) {
    return (
      <>
        <p className={styles.emptyState}>공개된 방문 맛집이 없습니다.</p>
        {pageInfo.totalElements > 0 ? (
          <CreatorPageNav
            page={pageInfo}
            pending={pending}
            onMove={(nextPage) => void load(nextPage)}
          />
        ) : null}
      </>
    )
  }

  return (
    <>
      <ul className={styles.restaurantList}>
        {items.map((restaurant) => (
          <li key={restaurant.id}>
            <Card
              title={
                <Link href={`/restaurants/${encodeURIComponent(restaurant.id)}`}>
                  {restaurant.name}
                </Link>
              }
              level={3}
              meta={`${restaurant.district} · ${restaurant.category}`}
            />
          </li>
        ))}
      </ul>
      <CreatorPageNav
        page={pageInfo}
        pending={pending}
        onMove={(nextPage) => void load(nextPage)}
      />
    </>
  )
}
