import styles from './curations.module.css'

export default function PublicCurationsLoading() {
  return (
    <section className={styles.page} aria-busy="true" aria-live="polite">
      <h1>큐레이션</h1>
      <p className={styles.loading}>큐레이션을 불러오는 중입니다.</p>
    </section>
  )
}
