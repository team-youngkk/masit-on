'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useCallback, useEffect, useRef, useState } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import {
  getFavorites,
  getRecentRestaurants,
  removeRecentRestaurant,
  setFavoriteState,
  type FavoriteRestaurantsResponse,
  type PersonalRestaurantPage,
  type PersonalRestaurantSummary,
  type RecentRestaurantsResponse,
} from '@/lib/member/personal-restaurants'

import styles from './personal-restaurants.module.css'

type ListKind = 'favorites' | 'recent'

type DisplayItem = {
  restaurant: PersonalRestaurantSummary
  timestamp: string
}

type DisplayPage = {
  items: DisplayItem[]
  page: PersonalRestaurantPage
}

type ErrorState = {
  status?: number
  message: string
  traceId?: string
}

type PersonalRestaurantListProps = {
  kind: ListKind
  page: number
  size: number
}

type ApiErrorBody = {
  message?: string
  traceId?: string
}

const listCopy = {
  favorites: {
    title: '찜한 맛집',
    empty: '아직 찜한 맛집이 없습니다.',
    timestamp: '찜한 시각',
    remove: '찜 해제',
    pending: '해제 중…',
    fallback: '찜 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
    removeFallback: '찜을 해제하지 못했습니다.',
    path: '/me/favorites',
  },
  recent: {
    title: '최근 본 맛집',
    empty: '최근에 본 맛집이 없습니다.',
    timestamp: '마지막으로 본 시각',
    remove: '기록 삭제',
    pending: '삭제 중…',
    fallback: '최근 본 맛집을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
    removeFallback: '최근 기록을 삭제하지 못했습니다.',
    path: '/me/recent-restaurants',
  },
} as const

function asDisplayPage(
  kind: ListKind,
  response: FavoriteRestaurantsResponse | RecentRestaurantsResponse,
): DisplayPage {
  return {
    items: response.items.map((item) => ({
      restaurant: item.restaurant,
      timestamp:
        kind === 'favorites'
          ? (item as FavoriteRestaurantsResponse['items'][number]).favoritedAt
          : (item as RecentRestaurantsResponse['items'][number]).lastViewedAt,
    })),
    page: response.page,
  }
}

async function toError(error: unknown, fallback: string): Promise<ErrorState> {
  if (!(error instanceof Response)) return { message: fallback }

  let body: ApiErrorBody | null = null
  try {
    body = (await error.json()) as ApiErrorBody
  } catch {
    // An empty or malformed error response still retains its HTTP status.
  }
  return {
    status: error.status,
    message: body?.message ?? fallback,
    traceId: body?.traceId,
  }
}

function formatTimestamp(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function pageNumbers(current: number, total: number): number[] {
  if (total < 1) return []
  const start = Math.max(1, Math.min(current - 2, total - 4))
  const end = Math.min(total, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
}

export function PersonalRestaurantList({
  kind,
  page,
  size,
}: PersonalRestaurantListProps) {
  const router = useRouter()
  const { status } = useMemberSession()
  const copy = listCopy[kind]
  const requestSequence = useRef(0)
  const deletionInFlight = useRef(false)
  const currentViewKey = `${kind}:${page}:${size}`
  const currentViewKeyRef = useRef(currentViewKey)
  currentViewKeyRef.current = currentViewKey
  const [data, setData] = useState<DisplayPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ErrorState | null>(null)
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set())
  const [itemErrors, setItemErrors] = useState<Record<string, ErrorState>>({})

  const load = useCallback(async (): Promise<DisplayPage | null> => {
    const sequence = ++requestSequence.current
    setLoading(true)
    setError(null)
    try {
      const response =
        kind === 'favorites'
          ? await getFavorites(page, size)
          : await getRecentRestaurants(page, size)
      if (sequence !== requestSequence.current) return null
      const displayPage = asDisplayPage(kind, response)
      setData(displayPage)
      return displayPage
    } catch (caught) {
      if (sequence !== requestSequence.current) return null
      const nextError = await toError(caught, copy.fallback)
      if (sequence !== requestSequence.current) return null
      setError(nextError)
      return null
    } finally {
      if (sequence === requestSequence.current) setLoading(false)
    }
  }, [copy.fallback, kind, page, size])

  useEffect(() => {
    if (status !== 'authenticated') {
      requestSequence.current += 1
      setData(null)
      setError(null)
      setPendingIds(new Set())
      setItemErrors({})
      setLoading(status === 'loading')
      return
    }

    void load()
    return () => {
      requestSequence.current += 1
    }
  }, [load, status])

  function goToPage(nextPage: number) {
    if (deletionInFlight.current) return
    router.push(`${copy.path}?page=${nextPage}&size=${size}`)
  }

  const currentRoute = `${copy.path}?page=${page}&size=${size}`
  const loginHref = `/login?returnTo=${encodeURIComponent(currentRoute)}`

  async function remove(restaurantId: string) {
    if (deletionInFlight.current) return
    deletionInFlight.current = true
    const viewKeyAtStart = currentViewKeyRef.current
    setPendingIds((current) => new Set(current).add(restaurantId))
    setItemErrors((current) => {
      const next = { ...current }
      delete next[restaurantId]
      return next
    })

    try {
      if (kind === 'favorites') {
        await setFavoriteState(restaurantId, false)
      } else {
        await removeRecentRestaurant(restaurantId)
      }

      if (viewKeyAtStart !== currentViewKeyRef.current) return

      const refreshedPage = await load()
      if (viewKeyAtStart !== currentViewKeyRef.current) return
      if (page > 1 && refreshedPage?.items.length === 0) {
        deletionInFlight.current = false
        goToPage(page - 1)
      }
    } catch (caught) {
      if (viewKeyAtStart !== currentViewKeyRef.current) return
      const itemError = await toError(caught, copy.removeFallback)
      if (viewKeyAtStart !== currentViewKeyRef.current) return
      if (itemError.status === 401) {
        setError(itemError)
      } else {
        setItemErrors((current) => ({ ...current, [restaurantId]: itemError }))
      }
    } finally {
      setPendingIds((current) => {
        const next = new Set(current)
        next.delete(restaurantId)
        return next
      })
      deletionInFlight.current = false
    }
  }

  return (
    <section>
      <h1>{copy.title}</h1>

      {status === 'loading' ? (
        <p className={styles.state} aria-live="polite">목록을 불러오는 중입니다.</p>
      ) : status === 'anonymous' || error?.status === 401 ? (
        <div className={styles.state} role="alert">
          <p>로그인이 만료되었습니다. 다시 로그인해 주세요.</p>
          <Link href={loginHref} className={styles.cta}>로그인하기</Link>
        </div>
      ) : loading && !data ? (
        <p className={styles.state} aria-live="polite">목록을 불러오는 중입니다.</p>
      ) : error ? (
        <div className={styles.error} role="alert">
          <p>{error.message}</p>
          {error.traceId ? <p className={styles.traceId}>traceId: {error.traceId}</p> : null}
          <Button variant="secondary" onClick={() => void load()}>다시 시도</Button>
        </div>
      ) : data?.items.length === 0 ? (
        <div className={styles.state}>
          <p>{copy.empty}</p>
          <Link href="/restaurants" className={styles.cta}>맛집 탐색하기</Link>
        </div>
      ) : data ? (
        <>
          <ul className={styles.list} aria-busy={loading}>
            {data.items.map((item) => {
              const id = item.restaurant.id
              const pending = pendingIds.has(id)
              const itemError = itemErrors[id]
              return (
                <li key={id}>
                  <Card
                    level={2}
                    title={<Link href={`/restaurants/${encodeURIComponent(id)}`}>{item.restaurant.name}</Link>}
                    meta={`${item.restaurant.district} · ${item.restaurant.category}`}
                  >
                    <p className={styles.timestamp}>
                      {copy.timestamp}: {formatTimestamp(item.timestamp)}
                    </p>
                    <div className={styles.actions}>
                      <Button
                        variant="secondary"
                        disabled={pendingIds.size > 0}
                        aria-describedby={itemError ? `remove-error-${id}` : undefined}
                        onClick={() => void remove(id)}
                      >
                        {pending ? copy.pending : itemError ? '다시 시도' : copy.remove}
                      </Button>
                    </div>
                    {itemError ? (
                      <p id={`remove-error-${id}`} className={styles.itemError} role="alert">
                        {itemError.message}
                        {itemError.traceId ? <span className={styles.traceId}>traceId: {itemError.traceId}</span> : null}
                      </p>
                    ) : null}
                  </Card>
                </li>
              )
            })}
          </ul>

          {data.page.totalPages > 1 ? (
            <nav className={styles.pagination} aria-label={`${copy.title} 페이지 이동`}>
              <p className={styles.pageStatus}>
                {data.page.number} / {data.page.totalPages} 페이지 (총 {data.page.totalElements}건)
              </p>
              <div className={styles.pageLinks}>
                <Button variant="secondary" disabled={data.page.number <= 1 || pendingIds.size > 0} onClick={() => goToPage(data.page.number - 1)}>이전</Button>
                {pageNumbers(data.page.number, data.page.totalPages).map((number) => (
                  <button
                    key={number}
                    type="button"
                    className={number === data.page.number ? styles.currentPage : styles.pageButton}
                    aria-current={number === data.page.number ? 'page' : undefined}
                    disabled={pendingIds.size > 0}
                    onClick={() => goToPage(number)}
                  >
                    {number}
                  </button>
                ))}
                <Button variant="secondary" disabled={!data.page.hasNext || pendingIds.size > 0} onClick={() => goToPage(data.page.number + 1)}>다음</Button>
              </div>
            </nav>
          ) : null}
        </>
      ) : null}
    </section>
  )
}
