export type RecentViewSessionStatus =
  | 'loading'
  | 'authenticated'
  | 'anonymous'
  | 'unavailable'

export type RecentViewCoordinationState = {
  requestedRestaurantId: string | null
}

export type RecentViewCoordination = {
  state: RecentViewCoordinationState
  shouldRequest: boolean
}

export const initialRecentViewCoordinationState: RecentViewCoordinationState = {
  requestedRestaurantId: null,
}

export function coordinateRecentView(
  state: RecentViewCoordinationState,
  status: RecentViewSessionStatus,
  restaurantId: string,
): RecentViewCoordination {
  if (status !== 'authenticated') {
    return {
      state: initialRecentViewCoordinationState,
      shouldRequest: false,
    }
  }

  if (state.requestedRestaurantId === restaurantId) {
    return { state, shouldRequest: false }
  }

  return {
    state: { requestedRestaurantId: restaurantId },
    shouldRequest: true,
  }
}
