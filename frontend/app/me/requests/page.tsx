import { ParticipationRequestScreen } from '@/components/participation/ParticipationRequestScreen'
import type { RequestKind, TargetType } from '@/lib/member/participation'

type ParticipationRequestsPageProps = {
  searchParams: Promise<{
    kind?: string | string[]
    targetType?: string | string[]
    targetId?: string | string[]
  }>
}

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

export default async function ParticipationRequestsPage({ searchParams }: ParticipationRequestsPageProps) {
  const params = await searchParams
  const kind = firstValue(params.kind) === 'report' ? 'report' as RequestKind : 'submission' as RequestKind
  const targetType = firstValue(params.targetType)
  const initialTargetType: TargetType = targetType === 'CREATOR' || targetType === 'VIDEO' || targetType === 'VISIT_RELATIONSHIP'
    ? targetType
    : 'RESTAURANT'
  const targetId = firstValue(params.targetId)?.trim()
  const loginQuery = new URLSearchParams({ kind, targetType: initialTargetType })
  if (targetId) loginQuery.set('targetId', targetId)

  return <ParticipationRequestScreen
    initialKind={kind}
    initialTargetType={initialTargetType}
    initialTargetId={targetId}
    loginReturnTo={`/me/requests?${loginQuery.toString()}`}
  />
}
