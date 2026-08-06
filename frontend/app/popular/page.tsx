import Link from 'next/link'

import { Card } from '@/components/ui/Card'
import { fetchPopularRestaurants } from '@/lib/popular-restaurants-api'

import styles from './popular.module.css'
import { RetryButton } from './RetryButton'

/*
 * 인기 맛집은 검색·필터·페이지가 없는 고정 목록이라 URL 쿼리 상태를 두지 않는다
 * (docs/04-product/prd/discovery/popular-restaurants.md 3절, API-POPULAR-001).
 * 매 요청마다 새로 조회해 찜·공개 상태 변경을 즉시 반영한다.
 */
export default async function PopularRestaurantsPage() {
  const result = await fetchPopularRestaurants()
  const items = result.ok ? result.data.items : []

  return (
    <section>
      <h1>인기 맛집</h1>
      {/* 정렬 기준 설명은 상태와 무관하게 항상 표시한다(wireframe 4절 POPULAR-LIST). */}
      <p className={styles.subtitle}>현재 가장 많이 찜한 공개 맛집</p>

      {!result.ok ? (
        <p className={styles.error} role="alert">
          {result.message}
          {result.traceId ? (
            <span className={styles.traceId}>traceId: {result.traceId}</span>
          ) : null}
          <br />
          <RetryButton />
        </p>
      ) : items.length === 0 ? (
        <p className={styles.state}>현재 찜한 공개 맛집이 없습니다.</p>
      ) : (
        <ol className={styles.list}>
          {items.map((item) => (
            <li key={item.restaurantId} className={styles.item}>
              <span className={styles.rank}>{item.rank}</span>
              <Card
                title={
                  <Link href={`/restaurants/${encodeURIComponent(item.restaurantId)}`}>
                    {item.name}
                  </Link>
                }
                level={2}
                meta={`${item.roadAddress} · ${item.category}`}
              >
                <p className={styles.favoriteCount}>찜 {item.favoriteCount}</p>
              </Card>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
