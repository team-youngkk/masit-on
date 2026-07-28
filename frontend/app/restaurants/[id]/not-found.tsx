import Link from 'next/link'

import { Card } from '@/components/ui/Card'

import styles from './not-found.module.css'

/*
 * API-DETAIL-001: 없음·비공개·삭제를 구분하지 않는 404를 같은 화면으로 보여준다.
 */
export default function RestaurantNotFound() {
  return (
    <section className={styles.notFound}>
      <Card title="맛집을 찾을 수 없습니다" level={2}>
        <p>요청하신 맛집 정보가 존재하지 않거나 더 이상 제공되지 않습니다.</p>
        <Link href="/restaurants" className={styles.link}>
          맛집 탐색으로 돌아가기
        </Link>
      </Card>
    </section>
  )
}
