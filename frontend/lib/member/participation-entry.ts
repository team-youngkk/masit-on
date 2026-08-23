import type { RequestKind, TargetType } from './participation'

const TARGET_TYPES: TargetType[] = ['RESTAURANT', 'CREATOR', 'VIDEO', 'VISIT_RELATIONSHIP']

export type NewParticipationEntry = {
  kind: RequestKind
  targetType: TargetType
  targetId?: string
  isRestaurantReport: boolean
}

export function reportTargetType(targetType: TargetType, isRestaurantReport: boolean): TargetType {
  return isRestaurantReport ? 'RESTAURANT' : targetType
}

export function parseNewParticipationEntry(params: {
  kind?: string
  targetType?: string
  targetId?: string
}): NewParticipationEntry {
  const kind: RequestKind = params.kind === 'report' ? 'report' : 'submission'
  const hasExplicitTargetType = TARGET_TYPES.includes(params.targetType as TargetType)
  const targetType = hasExplicitTargetType
    ? params.targetType as TargetType
    : 'RESTAURANT'
  const targetId = params.targetId?.trim() || undefined

  return {
    kind,
    targetType,
    targetId,
    isRestaurantReport: kind === 'report' && params.targetType === 'RESTAURANT' && Boolean(targetId),
  }
}
