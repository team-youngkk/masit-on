import Link from 'next/link'

import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'
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
      eyebrow="공개 취재 기록"
      title="동네 취재 노트"
      description="지역을 천천히 살펴보며, 맛잇온이 공개한 맛집 기록을 읽어보세요."
    >
      {!result.ok ? (
        <ErrorState message={result.message} traceId={result.traceId} />
      ) : curations.length === 0 ? (
        <StatePanel
          title="공개된 취재 기록이 없습니다"
          description="새로운 기록이 준비되면 이곳에서 소개할게요."
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
            <section className={styles.previewFooter} aria-label="취재 기록 준비 안내">
              <div>
                <h2>다음 취재 기록을 준비하고 있어요</h2>
                <p>새로운 지역과 맛집 이야기를 곧 소개할게요.</p>
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
  const title = preview ? (
    <span>{curation.title}</span>
  ) : (
    <Link href={`/curations/${encodeURIComponent(curation.curationId)}`}>{curation.title}</Link>
  )
  const detailHref = preview
    ? `/restaurants?query=${encodeURIComponent(previewItems[0]?.name ?? '')}`
    : `/curations/${encodeURIComponent(curation.curationId)}`

  return (
    <article className={styles.featuredCard}>
      <div className={styles.featuredIntro}>
        <p className={styles.noteLabel}>먼저 읽어볼 기록</p>
        <div className={styles.featuredHeading}>
          <h2>{title}</h2>
          <p className={styles.recordCount}>{curation.items.length}곳</p>
        </div>
        <p className={styles.description}>{curation.description}</p>
        <Link href={detailHref} className={styles.detailLink}>
          {preview ? '맛집 탐색으로 이어가기' : '기록 전체 읽기'}
        </Link>
      </div>
      <div className={styles.featuredBody}>
        <div className={styles.sectionHeading}>
          <h3>구성 맛집</h3>
          <span>{previewItems.length}곳 미리보기</span>
        </div>
        {previewItems.length === 0 ? (
          <p className={styles.emptyPreview}>현재 공개 중인 구성 맛집이 없습니다.</p>
        ) : (
          <ol className={styles.featuredRestaurantList}>
            {previewItems.map((restaurant) => (
              <li className={styles.featuredRestaurant} key={restaurant.restaurantId}>
                <Link
                  href={preview ? `/restaurants?query=${encodeURIComponent(restaurant.name)}` : `/restaurants/${encodeURIComponent(restaurant.restaurantId)}`}
                  className={styles.restaurantName}
                >
                  {restaurant.name}
                </Link>
                <span className={styles.restaurantMeta}>{restaurant.roadAddress}</span>
              </li>
            ))}
          </ol>
        )}
      </div>
    </article>
  )
}

function CurationCard({ curation }: { curation: PublicCuration }) {
  const previewItems = curation.items.slice(0, PREVIEW_LIMIT)
  const remainingCount = curation.items.length - previewItems.length

  return (
    <article className={styles.curationCard}>
      <header className={styles.curationCardHeader}>
        <p className={styles.noteLabel}>공개 취재 기록</p>
        <p className={styles.recordCount}>{curation.items.length}곳</p>
        <h2>
          <Link href={`/curations/${encodeURIComponent(curation.curationId)}`}>
            {curation.title}
          </Link>
        </h2>
      </header>
      <p className={styles.description}>{curation.description}</p>
      {previewItems.length === 0 ? (
        <p className={styles.emptyPreview}>현재 공개 중인 구성 맛집이 없습니다.</p>
      ) : (
        <div className={styles.preview}>
          <h3>구성 맛집 미리보기</h3>
          <ol className={styles.curationRestaurantList}>
            {previewItems.map((restaurant) => (
              <li key={restaurant.restaurantId}>
                <Link
                  href={`/restaurants/${encodeURIComponent(restaurant.restaurantId)}`}
                  className={styles.restaurantName}
                >
                  {restaurant.name}
                </Link>
                <span className={styles.restaurantMeta}>{restaurant.roadAddress}</span>
              </li>
            ))}
          </ol>
          {remainingCount > 0 ? <p className={styles.previewMore}>외 {remainingCount}곳 더 있음</p> : null}
        </div>
      )}
      <Link
        href={`/curations/${encodeURIComponent(curation.curationId)}`}
        className={styles.detailLink}
      >
        기록 상세 보기
      </Link>
    </article>
  )
}

function ErrorState({ message, traceId }: { message: string; traceId?: string }) {
  return (
    <StatePanel
      title="취재 기록을 불러올 수 없습니다"
      description={message}
      tone="danger"
      traceId={traceId}
      actions={<RetryButton />}
    />
  )
}
