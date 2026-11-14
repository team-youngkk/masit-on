import Link from 'next/link'
import { notFound } from 'next/navigation'

import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'
import { fetchPublicCuration } from '@/lib/curations-api'

import { RetryButton } from '../RetryButton'
import styles from '../curations.module.css'

type PublicCurationDetailPageProps = {
  params: Promise<{ curationId: string }>
}

export default async function PublicCurationDetailPage({
  params,
}: PublicCurationDetailPageProps) {
  const { curationId } = await params
  const result = await fetchPublicCuration(curationId)

  if (!result.ok && result.kind === 'not-found') {
    notFound()
  }

  if (!result.ok) {
    return (
      <PageShell className={styles.page} title="동네 취재 노트">
        <StatePanel
          title="취재 기록을 불러올 수 없습니다"
          description={result.message}
          tone="danger"
          traceId={result.traceId}
          actions={<><RetryButton /><Link href="/curations" className={styles.backLink}>동네 취재 노트로 돌아가기</Link></>}
        />
      </PageShell>
    )
  }

  const curation = result.data

  return (
    <PageShell className={styles.page}>
      <article className={styles.detailArticle}>
        <header className={styles.detailHeader}>
          <Link href="/curations" className={styles.backLink}>
            동네 취재 노트
          </Link>
          <p className={styles.noteLabel}>공개 취재 기록</p>
          <h1>{curation.title}</h1>
          <p className={styles.detailDescription}>{curation.description}</p>
        </header>

        <section aria-labelledby="curation-restaurants-heading">
          <div className={styles.sectionHeading}>
            <h2 id="curation-restaurants-heading">구성 맛집</h2>
            <span>{curation.items.length}곳</span>
          </div>
          {curation.items.length === 0 ? (
            <StatePanel
              compact
              title="현재 공개 중인 구성 맛집이 없습니다"
              description="기록 설명은 계속 확인할 수 있습니다."
              actions={<Link href="/restaurants" className={styles.actionLink}>다른 맛집 탐색하기</Link>}
            />
          ) : (
            <ol className={styles.restaurantList}>
              {curation.items.map((restaurant) => (
                <li key={restaurant.restaurantId}>
                  <article className={styles.restaurantCard}>
                    <div className={styles.cardHeading}>
                      <h3>
                        <Link
                          href={`/restaurants/${encodeURIComponent(restaurant.restaurantId)}`}
                        >
                          {restaurant.name}
                        </Link>
                      </h3>
                      <p className={styles.cardAddress}>{restaurant.roadAddress}</p>
                    </div>
                    <Link
                      href={`/restaurants/${encodeURIComponent(restaurant.restaurantId)}`}
                      className={styles.detailLink}
                      aria-label={`${restaurant.name} 상세 보기`}
                    >
                      맛집 상세 보기
                    </Link>
                  </article>
                </li>
              ))}
            </ol>
          )}
        </section>
      </article>
    </PageShell>
  )
}
