export type PersonalListCoordination = Readonly<{
  viewKey: string
  activeDeletionId: number | null
  nextDeletionId: number
}>

export type PersonalListDeletion = Readonly<{
  id: number
  viewKey: string
}>

export type StartDeletionResult = Readonly<{
  coordination: PersonalListCoordination
  deletion: PersonalListDeletion | null
}>

export function createPersonalListCoordination(
  viewKey: string,
): PersonalListCoordination {
  return {
    viewKey,
    activeDeletionId: null,
    nextDeletionId: 1,
  }
}

export function updatePersonalListView(
  coordination: PersonalListCoordination,
  viewKey: string,
): PersonalListCoordination {
  if (coordination.viewKey === viewKey) return coordination
  return { ...coordination, viewKey }
}

export function canNavigatePersonalList(
  coordination: PersonalListCoordination,
): boolean {
  return coordination.activeDeletionId === null
}

export function startPersonalListDeletion(
  coordination: PersonalListCoordination,
): StartDeletionResult {
  if (coordination.activeDeletionId !== null) {
    return { coordination, deletion: null }
  }

  const deletion = {
    id: coordination.nextDeletionId,
    viewKey: coordination.viewKey,
  }
  return {
    coordination: {
      ...coordination,
      activeDeletionId: deletion.id,
      nextDeletionId: deletion.id + 1,
    },
    deletion,
  }
}

export function isCurrentPersonalListDeletion(
  coordination: PersonalListCoordination,
  deletion: PersonalListDeletion,
): boolean {
  return (
    coordination.activeDeletionId === deletion.id &&
    coordination.viewKey === deletion.viewKey
  )
}

export function finishPersonalListDeletion(
  coordination: PersonalListCoordination,
  deletion: PersonalListDeletion,
): PersonalListCoordination {
  if (coordination.activeDeletionId !== deletion.id) return coordination
  return { ...coordination, activeDeletionId: null }
}

export function previousPageAfterEmptyDeletionRefresh(
  coordination: PersonalListCoordination,
  deletion: PersonalListDeletion,
  page: number,
  refreshedItemCount: number | undefined,
): number | null {
  if (!isCurrentPersonalListDeletion(coordination, deletion)) return null
  if (page <= 1 || refreshedItemCount !== 0) return null
  return page - 1
}
