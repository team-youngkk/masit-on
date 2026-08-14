import type { MapPointsFetchResult } from './map-points-client'

/** 서버 prefetch로 복원된 429 결과를 클라이언트 최초 조회 차단 상태로 변환한다. */
export function initialMapRateLimitedUntil(
  hydratedData: MapPointsFetchResult | undefined,
): number | null {
  return hydratedData?.kind === 'rateLimited' ? hydratedData.retryAvailableAt : null
}
