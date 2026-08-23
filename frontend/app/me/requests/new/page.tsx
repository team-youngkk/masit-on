import { ParticipationRequestScreen } from '@/components/participation/ParticipationRequestScreen'
import type { RequestKind, TargetType } from '@/lib/member/participation'

type NewParticipationRequestPageProps = {
  searchParams: Promise<{
    kind?: string | string[]
    targetType?: string | string[]
    targetId?: string | string[]
    targetLabel?: string | string[]
  }>
}

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

export default async function NewParticipationRequestPage({ searchParams }: NewParticipationRequestPageProps) {
  const params = await searchParams
  const kind = firstValue(params.kind) === 'report' ? 'report' as RequestKind : 'submission' as RequestKind
  const targetType = firstValue(params.targetType)
  const initialTargetType: TargetType = targetType === 'CREATOR' || targetType === 'VIDEO' || targetType === 'VISIT_RELATIONSHIP'
    ? targetType
    : 'RESTAURANT'
  const targetId = firstValue(params.targetId)?.trim()
  const targetLabel = firstValue(params.targetLabel)?.trim()
  const loginQuery = new URLSearchParams({ kind, targetType: initialTargetType })
  if (targetId) loginQuery.set('targetId', targetId)
  if (targetLabel) loginQuery.set('targetLabel', targetLabel)

  return <ParticipationRequestScreen
    view="new"
    initialKind={kind}
    initialTargetType={initialTargetType}
    initialTargetId={targetId}
    initialTargetLabel={targetLabel}
    loginReturnTo={`/me/requests/new?${loginQuery.toString()}`}
  />
}
