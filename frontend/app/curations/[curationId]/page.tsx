import Link from 'next/link'
import { notFound } from 'next/navigation'

import { Card } from '@/components/ui/Card'
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
      <PageShell className={styles.page} title="큐레이션">
        <StatePanel
          title="큐레이션을 불러올 수 없습니다"
          description={result.message}
          tone="danger"
          traceId={result.traceId}
          actions={<><RetryButton /><Link href="/curations" className={styles.backLink}>큐레이션 탐색으로 돌아가기</Link></>}
        />
      </PageShell>
    )
  }

  const curation = result.data

  return (
    <PageShell className={styles.page}>
    <article>
      <header className={styles.detailHeader}>
        <Link href="/curations" className={styles.backLink}>
          큐레이션 목록
        </Link>
        <h1>{curation.title}</h1>
        <p className={styles.detailDescription}>{curation.description}</p>
      </header>

      <section aria-labelledby="curation-restaurants-heading">
        <h2 id="curation-restaurants-heading">구성 맛집</h2>
        {curation.items.length === 0 ? (
          <StatePanel
            compact
            title="현재 공개 중인 구성 맛집이 없습니다"
            description="큐레이션 설명은 계속 확인할 수 있습니다."
            actions={<Link href="/restaurants" className={styles.actionLink}>다른 맛집 탐색하기</Link>}
          />
        ) : (
          <ol className={styles.restaurantList}>
            {curation.items.map((restaurant) => (
              <li key={restaurant.restaurantId}>
                <Card
                  title={
                    <Link
                      href={`/restaurants/${encodeURIComponent(restaurant.restaurantId)}`}
                    >
                      {restaurant.name}
                    </Link>
                  }
                  level={3}
                  meta={restaurant.roadAddress}
                />
              </li>
            ))}
          </ol>
        )}
      </section>
    </article>
    </PageShell>
  )
}
