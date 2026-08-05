import Link from 'next/link'

import styles from '../curations.module.css'

export default function PublicCurationNotFound() {
  return (
    <section className={styles.state}>
      <h1>큐레이션을 찾을 수 없습니다</h1>
      <p>요청하신 큐레이션이 존재하지 않거나 게시가 종료되었습니다.</p>
      <Link href="/curations" className={styles.actionLink}>
        큐레이션 탐색으로 돌아가기
      </Link>
    </section>
  )
}
