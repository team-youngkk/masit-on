import Link from 'next/link'

import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { isDesignPreviewEnvironment } from '@/lib/design-preview'
import { getRestaurantPlaceholderImage } from '@/lib/restaurant-placeholder-image'
import {
  fetchPopularRestaurants,
  type PopularRestaurantItem,
} from '@/lib/popular-restaurants-api'

import styles from './popular.module.css'
import { RetryButton } from './RetryButton'

const POPULAR_DESIGN_PREVIEW_ITEMS: PopularRestaurantItem[] = [
  {
    rank: 1,
    restaurantId: 'popular-preview-1',
    name: '을지로 고기곰탕',
    roadAddress: '중구 을지로',
    category: '한식',
    favoriteCount: 12430,
  },
  {
    rank: 2,
    restaurantId: 'popular-preview-2',
    name: '연남동 분식집',
    roadAddress: '마포구 연남동',
    category: '분식',
    favoriteCount: 9812,
  },
  {
    rank: 3,
    restaurantId: 'popular-preview-3',
    name: '서래마을 파스타',
    roadAddress: '서초구 서래로',
    category: '양식',
    favoriteCount: 9765,
  },
  {
    rank: 4,
    restaurantId: 'popular-preview-4',
    name: '망원동 연탄구이',
    roadAddress: '마포구 망원동',
    category: '고기·구이',
    favoriteCount: 7654,
  },
  {
    rank: 5,
    restaurantId: 'popular-preview-5',
    name: '상수 스시오마카세',
    roadAddress: '마포구 상수동',
    category: '일식',
    favoriteCount: 6321,
  },
]

/*
 * 인기 맛집은 검색·필터·페이지가 없는 고정 목록이라 URL 쿼리 상태를 두지 않는다
 * (docs/04-product/prd/discovery/popular-restaurants.md 3절, API-POPULAR-001).
 * 매 요청마다 새로 조회해 찜·공개 상태 변경을 즉시 반영한다.
 */
export default async function PopularRestaurantsPage() {
  const result = await fetchPopularRestaurants()
  const items = result.ok ? result.data.items : []
  const isDesignPreview =
    isDesignPreviewEnvironment({
      nodeEnv: process.env.NODE_ENV,
      previewFlag: process.env.MASITON_UI_PREVIEW,
    }) &&
    (!result.ok || items.length === 0)
  const displayItems = isDesignPreview ? POPULAR_DESIGN_PREVIEW_ITEMS : items
  const previewApiWarning = isDesignPreview && !result.ok

  return (
    <PageShell
      title="인기 맛집"
      description="지금 가장 많은 관심을 받는 공개 맛집이에요."
    >
      {/* 정렬 기준 설명은 상태와 무관하게 항상 표시한다(wireframe 4절 POPULAR-LIST). */}
      <p className={styles.subtitle}>현재 가장 많이 찜한 공개 맛집</p>

      {!result.ok && !isDesignPreview ? (
        <StatePanel
          title="인기 맛집을 불러올 수 없습니다"
          description={result.message}
          tone="danger"
          traceId={result.traceId}
          actions={<RetryButton />}
        />
      ) : displayItems.length === 0 ? (
        <StatePanel
          title="아직 인기 맛집이 없습니다"
          description="찜한 공개 맛집이 생기면 이곳에 순위가 표시됩니다."
          compact
        />
      ) : (
        <>
          {previewApiWarning ? (
            <StatePanel
              compact
              tone="warning"
              title="개발용 디자인 프리뷰"
              description="인기 맛집 API가 연결되지 않아 더미 데이터로 표시하고 있습니다."
            />
          ) : null}
          <ol className={styles.list}>
            {displayItems.map((item) => (
              <li key={item.restaurantId} className={styles.item}>
                <article className={styles.popularCard}>
                <div className={styles.cardMedia}>
                  <img
                    src={
                      getRestaurantPlaceholderImage(
                        item.restaurantId,
                        item.category,
                      ).src
                    }
                    alt=""
                    className={styles.cardMediaImage}
                    loading="lazy"
                    decoding="async"
                  />
                </div>
                <div className={styles.cardBody}>
                  <div className={styles.cardTitleRow}>
                    <span
                      className={styles.rank}
                      aria-label={`인기 ${item.rank}위`}
                    >
                      {item.rank}
                    </span>
                    <h2>
                      <Link
                        href={
                          isDesignPreview
                            ? `/restaurants?query=${encodeURIComponent(item.name)}`
                            : `/restaurants/${encodeURIComponent(item.restaurantId)}`
                        }
                      >
                        {item.name}
                      </Link>
                    </h2>
                  </div>
                  <p className={styles.cardMeta}>
                    {item.roadAddress} · {item.category}
                  </p>
                  <StatusBadge className={styles.favoriteBadge} tone="success">
                    찜 {item.favoriteCount.toLocaleString()}
                  </StatusBadge>
                  <Link
                    href={
                      isDesignPreview
                        ? `/restaurants?query=${encodeURIComponent(item.name)}`
                        : `/restaurants/${encodeURIComponent(item.restaurantId)}`
                    }
                    className={styles.detailLink}
                    aria-label={`${item.name} 상세 보기`}
                  >
                    상세 보기 <span aria-hidden="true">→</span>
                  </Link>
                </div>
                </article>
              </li>
            ))}
          </ol>
        </>
      )}
    </PageShell>
  )
}
