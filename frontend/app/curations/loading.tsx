import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'

import styles from './curations.module.css'

export default function PublicCurationsLoading() {
  return (
    <PageShell className={styles.page} title="큐레이션">
      <StatePanel compact title="큐레이션을 불러오는 중입니다" />
    </PageShell>
  )
}
