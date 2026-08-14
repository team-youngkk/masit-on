import type { MapPointsFetchResult } from './map-points-client'

export type MapRateLimitState = Readonly<Record<string, number>>

function rateLimitStateKey(queryKey: readonly unknown[]): string {
  return JSON.stringify(queryKey)
}

/** 서버 prefetch로 복원된 429 결과를 클라이언트 최초 조회 차단 상태로 변환한다. */
export function initialMapRateLimitedUntil(
  hydratedData: MapPointsFetchResult | undefined,
): number | null {
  return hydratedData?.kind === 'rateLimited' ? hydratedData.retryAvailableAt : null
}

export function createMapRateLimitState(
  queryKey: readonly unknown[],
  hydratedData: MapPointsFetchResult | undefined,
): MapRateLimitState {
  return mergeHydratedMapRateLimitState({}, queryKey, hydratedData)
}

export function mergeHydratedMapRateLimitState(
  state: MapRateLimitState,
  queryKey: readonly unknown[],
  hydratedData: MapPointsFetchResult | undefined,
): MapRateLimitState {
  const retryAvailableAt = initialMapRateLimitedUntil(hydratedData)
  return retryAvailableAt === null
    ? state
    : setMapRateLimitedUntil(state, queryKey, retryAvailableAt)
}

export function getMapRateLimitedUntil(
  state: MapRateLimitState,
  queryKey: readonly unknown[],
): number | null {
  return state[rateLimitStateKey(queryKey)] ?? null
}

export function setMapRateLimitedUntil(
  state: MapRateLimitState,
  queryKey: readonly unknown[],
  retryAvailableAt: number | null,
): MapRateLimitState {
  const key = rateLimitStateKey(queryKey)
  if (retryAvailableAt !== null) {
    if (state[key] === retryAvailableAt) {
      return state
    }
    return { ...state, [key]: retryAvailableAt }
  }
  if (!(key in state)) {
    return state
  }

  const next = { ...state }
  delete next[key]
  return next
}
