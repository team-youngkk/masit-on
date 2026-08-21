import Link from 'next/link'

import { Card } from '@/components/ui/Card'
import { PageShell } from '@/components/ui/PageShell'

import styles from './not-found.module.css'

/*
 * API-CREATOR-DETAIL-001 3절, PRD-DETAIL-002 9절: 없음·비공개·삭제·외부 이용
 * 불가를 구분하지 않는 같은 찾을 수 없음 화면으로 보여준다.
 */
export default function CreatorNotFound() {
  return (
    <PageShell title="유튜버 상세" size="narrow">
      <section className={styles.notFound}>
        <Card title="유튜버를 찾을 수 없습니다" level={2}>
          <p>요청하신 유튜버 정보가 존재하지 않거나 더 이상 제공되지 않습니다.</p>
          <Link href="/restaurants" className={styles.link}>
            맛집 탐색으로 돌아가기
          </Link>
        </Card>
      </section>
    </PageShell>
  )
}
