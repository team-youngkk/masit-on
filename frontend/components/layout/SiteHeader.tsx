import Link from 'next/link'

import styles from './SiteHeader.module.css'

/*
 * 1차 MVP 헤더.
 * 와이어프레임의 지도·테마·보관함·로그인·회원가입은 제외 범위이므로 노출하지 않는다.
 * 관리자 진입은 /admin/login으로 분리되어 공개 화면에 링크를 두지 않는다.
 */
export function SiteHeader() {
  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link href="/restaurants" className={styles.brand}>
          <span className={styles.mark} aria-hidden="true">
            ●
          </span>
          맛잇온
        </Link>
        <nav className={styles.nav} aria-label="주요 메뉴">
          <Link href="/restaurants">맛집 탐색</Link>
        </nav>
      </div>
    </header>
  )
}
