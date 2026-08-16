import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'

import styles from '../curations.module.css'

export default function PublicCurationDetailLoading() {
  return (
    <div aria-busy="true" aria-live="polite">
      <PageShell className={styles.page} title="큐레이션">
        <StatePanel compact title="구성 맛집을 확인하고 있습니다" />
      </PageShell>
    </div>
  )
}
