import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'

export default function CreatorDetailLoading() {
  return (
    <PageShell title="유튜버 상세">
      <div aria-busy="true" aria-live="polite">
        <StatePanel title="유튜버 정보를 불러오는 중입니다" />
      </div>
    </PageShell>
  )
}
