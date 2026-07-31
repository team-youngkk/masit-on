import { HydrationBoundary, QueryClient, dehydrate } from '@tanstack/react-query'

import { MapScreen } from '@/components/map/MapScreen'
import { SEOUL_FALLBACK_BOUNDS } from '@/lib/map/map-points-query'
import { fetchMapPointsOnServer } from '@/lib/map/map-points-server'
import { fetchCreators, toSingleValue, type RawSearchParams } from '@/lib/restaurants-api'

type MapPageProps = {
  searchParams: Promise<RawSearchParams>
}

/*
 * 지도 화면의 이름·자치구·음식 카테고리·유튜버 조건만 URL 쿼리로 공유 가능하게 읽는다.
 * 지도 bounds는 이 화면에서 다루지 않고 MapScreen의 client 전용 state로만 관리한다
 * (ADR-WEB-002, ADR-MAP-001 6.6).
 */
export default async function MapPage({ searchParams }: MapPageProps) {
  const rawParams = await searchParams
  const creatorsResult = await fetchCreators()

  const initialFilters = {
    query: toSingleValue(rawParams.query)?.trim() || undefined,
    district: toSingleValue(rawParams.district) || undefined,
    category: toSingleValue(rawParams.category) || undefined,
    creatorId: toSingleValue(rawParams.creatorId) || undefined,
  }

  /*
   * ADR-WEB-002: 최초 응답에 실제 지도 결과가 있도록 MapScreen의 client useQuery가
   * 처음 렌더링에서 만드는 것과 정확히 같은 조건(SEOUL_FALLBACK_BOUNDS, initialFilters)과
   * queryKey 형태로 서버에서 미리 조회해 hydrate한다. 형태가 조금이라도 다르면 hydration이
   * 조용히 무시되고 클라이언트가 다시 조회하므로 MapScreen.tsx의 queryKey와 반드시 맞춘다.
   */
  const queryClient = new QueryClient()
  await queryClient.prefetchQuery({
    queryKey: [
      'map-points',
      SEOUL_FALLBACK_BOUNDS,
      initialFilters.query ?? '',
      initialFilters.district ?? '',
      initialFilters.category ?? '',
      initialFilters.creatorId ?? '',
    ],
    queryFn: () => fetchMapPointsOnServer(SEOUL_FALLBACK_BOUNDS, initialFilters),
  })
  const dehydratedState = dehydrate(queryClient)

  return (
    <HydrationBoundary state={dehydratedState}>
      <MapScreen initialFilters={initialFilters} creatorsResult={creatorsResult} />
    </HydrationBoundary>
  )
}
