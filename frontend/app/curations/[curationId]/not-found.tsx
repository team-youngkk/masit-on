import Link from 'next/link'

import styles from '../curations.module.css'

export default function PublicCurationNotFound() {
  return (
    <section className={styles.state}>
      <h1>취재 기록을 찾을 수 없습니다</h1>
      <p>요청하신 기록이 존재하지 않거나 게시가 종료되었습니다.</p>
      <Link href="/curations" className={styles.actionLink}>
        동네 취재 노트로 돌아가기
      </Link>
    </section>
  )
}
