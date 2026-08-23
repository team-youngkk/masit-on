import { ParticipationRequestScreen } from '@/components/participation/ParticipationRequestScreen'
import {
  RestaurantDetailUnavailableError,
  RestaurantIdentifierInvalidError,
  RestaurantNotFoundError,
  getRestaurantDetail,
} from '@/lib/api'
import type { RequestKind, TargetType } from '@/lib/member/participation'
import { parseNewParticipationEntry } from '@/lib/member/participation-entry'

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
  const entry = parseNewParticipationEntry({
    kind: firstValue(params.kind),
    targetType: firstValue(params.targetType),
    targetId: firstValue(params.targetId),
  })
  const restaurantContext = entry.isRestaurantReport && entry.targetId
    ? await loadRestaurantContext(entry.targetId)
    : null
  const contextualReport = restaurantContext?.status === 'found' ? restaurantContext : null
  const initialLoadError = restaurantContext?.status === 'unavailable'
    ? { message: '맛집 정보를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.', traceId: restaurantContext.traceId }
    : undefined
  const kind: RequestKind = entry.kind
  const initialTargetType: TargetType = entry.targetType
  const targetId = contextualReport?.id ?? entry.targetId
  const loginQuery = new URLSearchParams({ kind, targetType: initialTargetType })
  if (targetId) loginQuery.set('targetId', targetId)

  return <ParticipationRequestScreen
    view="new"
    initialKind={kind}
    initialTargetType={initialTargetType}
    initialTargetId={targetId}
    initialTargetLabel={contextualReport?.name}
    initialTargetVerified={Boolean(contextualReport)}
    initialLoadError={initialLoadError}
    loginReturnTo={`/me/requests/new?${loginQuery.toString()}`}
  />
}

type RestaurantContext =
  | { status: 'found'; id: string; name: string }
  | { status: 'fallback' }
  | { status: 'unavailable'; traceId?: string }

async function loadRestaurantContext(restaurantId: string): Promise<RestaurantContext> {
  try {
    const restaurant = await getRestaurantDetail(restaurantId)
    return { status: 'found', id: restaurant.id, name: restaurant.name }
  } catch (reason) {
    if (reason instanceof RestaurantNotFoundError || reason instanceof RestaurantIdentifierInvalidError) {
      return { status: 'fallback' }
    }
    if (reason instanceof RestaurantDetailUnavailableError) {
      return { status: 'unavailable', traceId: reason.traceId }
    }
    return { status: 'unavailable' }
  }
}
