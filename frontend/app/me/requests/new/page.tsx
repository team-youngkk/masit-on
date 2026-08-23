import { ParticipationRequestScreen } from '@/components/participation/ParticipationRequestScreen'
import { getRestaurantDetail } from '@/lib/api'
import type { RequestKind, TargetType } from '@/lib/member/participation'

type NewParticipationRequestPageProps = {
  searchParams: Promise<{
    kind?: string | string[]
    targetType?: string | string[]
    targetId?: string | string[]
  }>
}

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

export default async function NewParticipationRequestPage({ searchParams }: NewParticipationRequestPageProps) {
  const params = await searchParams
  const requestedKind = firstValue(params.kind)
  const requestedTargetType = firstValue(params.targetType)
  const requestedTargetId = firstValue(params.targetId)?.trim()
  const contextualReport = requestedKind === 'report' && requestedTargetType === 'RESTAURANT' && requestedTargetId
    ? await loadRestaurantContext(requestedTargetId)
    : null
  const kind = contextualReport ? 'report' as RequestKind : 'submission' as RequestKind
  const initialTargetType: TargetType = 'RESTAURANT'
  const loginQuery = new URLSearchParams({ kind, targetType: initialTargetType })
  if (contextualReport) loginQuery.set('targetId', contextualReport.id)

  return <ParticipationRequestScreen
    view="new"
    initialKind={kind}
    initialTargetType={initialTargetType}
    initialTargetId={contextualReport?.id}
    initialTargetLabel={contextualReport?.name}
    loginReturnTo={`/me/requests/new?${loginQuery.toString()}`}
  />
}

async function loadRestaurantContext(restaurantId: string): Promise<{ id: string; name: string } | null> {
  try {
    const restaurant = await getRestaurantDetail(restaurantId)
    return { id: restaurant.id, name: restaurant.name }
  } catch {
    return null
  }
}
