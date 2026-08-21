'use client'

import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'

import styles from './page.module.css'

export default function CreatorDetailError({ reset }: { reset: () => void }) {
  return (
    <PageShell title="유튜버 상세">
      <StatePanel
        tone="danger"
        title="유튜버 정보를 불러올 수 없습니다"
        description="일시적으로 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요."
        actions={(
          <button type="button" className={styles.retryLink} onClick={reset}>
            다시 시도
          </button>
        )}
      />
    </PageShell>
  )
}
