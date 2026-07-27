import styles from './SiteFooter.module.css'

/*
 * 1차 MVP 푸터.
 * 와이어프레임의 고객센터·공지사항·광고문의·약관 링크는 대상 화면이 MVP 범위에 없다.
 * "준비 중 링크를 노출하지 않는다"는 시각 규칙에 따라 브랜드와 저작권만 남긴다.
 */
export function SiteFooter() {
  return (
    <footer className={styles.footer}>
      <div className={styles.inner}>
        <span className={styles.brand}>
          <span className={styles.mark} aria-hidden="true">
            ●
          </span>
          맛잇온
        </span>
        <p className={styles.tagline}>
          유튜버가 방문한 맛집을 지역·음식 종류·유튜버로 찾아보세요.
        </p>
        <p className={styles.copyright}>© 2026 MASIT-ON</p>
      </div>
    </footer>
  )
}
