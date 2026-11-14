import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'

import styles from './curations.module.css'

export default function PublicCurationsLoading() {
  return (
    <div aria-busy="true" aria-live="polite">
      <PageShell className={styles.page} title="동네 취재 노트">
        <StatePanel compact title="공개 취재 기록을 불러오는 중입니다" />
      </PageShell>
    </div>
  )
}
