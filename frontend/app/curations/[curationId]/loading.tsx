import styles from '../curations.module.css'

export default function PublicCurationDetailLoading() {
  return (
    <section className={styles.page} aria-busy="true" aria-live="polite">
      <h1>큐레이션을 불러오는 중입니다</h1>
      <p className={styles.loading}>구성 맛집을 확인하고 있습니다.</p>
    </section>
  )
}
