import Link from 'next/link'

import { Card } from '@/components/ui/Card'
import { fetchPublicCurations } from '@/lib/curations-api'

import styles from './curations.module.css'
import { RetryButton } from './RetryButton'

const PREVIEW_LIMIT = 3

export default async function PublicCurationsPage() {
  const result = await fetchPublicCurations()

  return (
    <section className={styles.page}>
      <header className={styles.pageHeader}>
        <h1>큐레이션</h1>
        <p>관리자가 직접 고른 주제별 맛집을 만나보세요.</p>
      </header>

      {!result.ok ? (
        <ErrorState message={result.message} traceId={result.traceId} />
      ) : result.data.items.length === 0 ? (
        <div className={styles.state}>
          <h2>게시 중인 큐레이션이 없습니다</h2>
          <p>새로운 큐레이션이 준비되면 이곳에서 소개할게요.</p>
          <Link href="/restaurants" className={styles.actionLink}>
            맛집 탐색하기
          </Link>
        </div>
      ) : (
        <ul className={styles.curationList}>
          {result.data.items.map((curation) => {
            const previewItems = curation.items.slice(0, PREVIEW_LIMIT)
            const remainingCount = curation.items.length - previewItems.length

            return (
              <li key={curation.curationId}>
                <Card
                  title={
                    <Link
                      href={`/curations/${encodeURIComponent(curation.curationId)}`}
                    >
                      {curation.title}
                    </Link>
                  }
                  level={2}
                >
                  <p className={styles.description}>{curation.description}</p>
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
                      {remainingCount > 0 ? (
                        <p>외 {remainingCount}곳</p>
                      ) : null}
                    </div>
                  )}
                  <Link
                    href={`/curations/${encodeURIComponent(curation.curationId)}`}
                    className={styles.detailLink}
                  >
                    큐레이션 자세히 보기
                  </Link>
                </Card>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

function ErrorState({ message, traceId }: { message: string; traceId?: string }) {
  return (
    <div className={styles.state} role="alert">
      <h2>큐레이션을 불러올 수 없습니다</h2>
      <p>{message}</p>
      {traceId ? <p className={styles.traceId}>traceId: {traceId}</p> : null}
      <RetryButton />
    </div>
  )
}
