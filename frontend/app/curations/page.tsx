import Link from 'next/link'

import { Card } from '@/components/ui/Card'
import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { fetchPublicCurations, type PublicCuration } from '@/lib/curations-api'
import { isDesignPreviewEnvironment } from '@/lib/design-preview'

import styles from './curations.module.css'
import { RetryButton } from './RetryButton'

const PREVIEW_LIMIT = 3

const DESIGN_PREVIEW_CURATIONS: PublicCuration[] = [
  {
    curationId: 'preview-rainy-day',
    title: '비 오는 날 생각나는 맛집',
    description: '빗소리와 함께 더 맛있는, 따뜻한 국물과 편안한 분위기의 맛집을 모았어요.',
    items: [
      { restaurantId: 'preview-restaurant-1', name: '을지로 고기곰탕', roadAddress: '서울 중구 을지로' },
      { restaurantId: 'preview-restaurant-2', name: '성수 수제비', roadAddress: '서울 성동구 성수동' },
      { restaurantId: 'preview-restaurant-3', name: '연희동 베이커리', roadAddress: '서울 서대문구 연희동' },
      { restaurantId: 'preview-restaurant-4', name: '한남동 카페', roadAddress: '서울 용산구 한남동' },
      { restaurantId: 'preview-restaurant-5', name: '망원동 칼국수', roadAddress: '서울 마포구 망원동' },
    ],
    publishedAt: '2025-05-23T09:00:00Z',
    updatedAt: '2025-05-23T09:00:00Z',
  },
]

export default async function PublicCurationsPage() {
  const result = await fetchPublicCurations()
  const isDesignPreview =
    isDesignPreviewEnvironment({
      nodeEnv: process.env.NODE_ENV,
      previewFlag: process.env.MASITON_UI_PREVIEW,
    }) &&
    result.ok &&
    result.data.items.length === 0
  const curations = isDesignPreview ? DESIGN_PREVIEW_CURATIONS : result.ok ? result.data.items : []

  return (
    <PageShell
      className={styles.page}
      title="큐레이션"
      description="관리자가 직접 고른 주제별 맛집을 만나보세요."
    >

      {!result.ok ? (
        <ErrorState message={result.message} traceId={result.traceId} />
      ) : curations.length === 0 ? (
        <StatePanel
          title="게시 중인 큐레이션이 없습니다"
          description="새로운 큐레이션이 준비되면 이곳에서 소개할게요."
          compact
          actions={<Link href="/restaurants" className={styles.actionLink}>맛집 탐색하기</Link>}
        />
      ) : (
        <>
          <FeaturedCuration curation={curations[0]} preview={isDesignPreview} />
          {curations.length > 1 ? (
            <ul className={styles.curationList}>
              {curations.slice(1).map((curation) => (
                <li key={curation.curationId}>
                  <CurationCard curation={curation} />
                </li>
              ))}
            </ul>
          ) : null}
          {isDesignPreview ? (
            <section className={styles.previewFooter} aria-label="큐레이션 준비 안내">
              <span className={styles.previewFooterIcon} aria-hidden="true">＋</span>
              <div>
                <h2>새로운 큐레이션을 준비하고 있어요</h2>
                <p>맛잇온이 고른 다음 주제별 맛집도 곧 만나보세요.</p>
              </div>
            </section>
          ) : null}
        </>
      )}
    </PageShell>
  )
}

function FeaturedCuration({ curation, preview }: { curation: PublicCuration; preview: boolean }) {
  const previewItems = curation.items.slice(0, 5)
  const mediaEyebrow = preview ? '오늘의 큐레이션' : '맛잇온 큐레이션'
  const mediaTitle = preview ? '비 오는 날의 따뜻한 한 끼' : curation.title
  const mediaIcon = preview ? '☂' : '✦'
  const title = preview ? (
    <span>{curation.title}</span>
  ) : (
    <Link href={`/curations/${encodeURIComponent(curation.curationId)}`}>{curation.title}</Link>
  )

  return (
    <article className={styles.featuredCard}>
      <div className={styles.featuredMedia} aria-hidden="true">
        <span className={styles.mediaEyebrow}>{mediaEyebrow}</span>
        <span className={styles.mediaTitle}>{mediaTitle}</span>
        <span className={styles.mediaRain}>{mediaIcon}</span>
      </div>
      <div className={styles.featuredBody}>
        <div className={styles.featuredHeading}>
          <div>
            <StatusBadge tone="success">공개</StatusBadge>
            <h2>{title}</h2>
          </div>
          <span className={styles.featuredArrow} aria-hidden="true">↗</span>
        </div>
        <p className={styles.description}>{curation.description}</p>
        <div className={styles.featuredRestaurants}>
          {previewItems.map((restaurant, index) => (
            <div className={styles.featuredRestaurant} key={restaurant.restaurantId}>
              <span className={styles.restaurantNumber}>{index + 1}</span>
              <span className={styles.restaurantName}>
                <Link
                  href={preview ? `/restaurants?query=${encodeURIComponent(restaurant.name)}` : `/restaurants/${encodeURIComponent(restaurant.restaurantId)}`}
                >
                  {restaurant.name}
                </Link>
              </span>
              <span className={styles.restaurantMeta}>{restaurant.roadAddress}</span>
            </div>
          ))}
        </div>
        {preview ? (
          <Link
            href={`/restaurants?query=${encodeURIComponent(previewItems[0]?.name ?? '')}`}
            className={styles.detailLink}
          >
            맛집 둘러보기 →
          </Link>
        ) : (
          <Link
            href={`/curations/${encodeURIComponent(curation.curationId)}`}
            className={styles.detailLink}
          >
            큐레이션 자세히 보기 →
          </Link>
        )}
      </div>
    </article>
  )
}

function CurationCard({ curation }: { curation: PublicCuration }) {
  const previewItems = curation.items.slice(0, PREVIEW_LIMIT)
  const remainingCount = curation.items.length - previewItems.length

  return (
    <Card
      title={
        <Link href={`/curations/${encodeURIComponent(curation.curationId)}`}>
          {curation.title}
        </Link>
      }
      level={2}
    >
      <p className={styles.description}>{curation.description}</p>
      <StatusBadge tone="success">공개</StatusBadge>
      {previewItems.length === 0 ? (
        <p className={styles.emptyPreview}>현재 공개 중인 구성 맛집이 없습니다.</p>
      ) : (
        <div className={styles.preview}>
          <h3>구성 맛집 미리보기</h3>
          <ul>
            {previewItems.map((restaurant) => (
              <li key={restaurant.restaurantId}>{restaurant.name}</li>
            ))}
          </ul>
          {remainingCount > 0 ? <p>외 {remainingCount}곳</p> : null}
        </div>
      )}
      <Link
        href={`/curations/${encodeURIComponent(curation.curationId)}`}
        className={styles.detailLink}
      >
        큐레이션 자세히 보기
      </Link>
    </Card>
  )
}

function ErrorState({ message, traceId }: { message: string; traceId?: string }) {
  return (
    <StatePanel
      title="큐레이션을 불러올 수 없습니다"
      description={message}
      tone="danger"
      traceId={traceId}
      actions={<RetryButton />}
    />
  )
}
