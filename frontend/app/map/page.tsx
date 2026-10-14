import Link from 'next/link'
import { HydrationBoundary, QueryClient, dehydrate } from '@tanstack/react-query'
import { headers } from 'next/headers'

import { MapScreen } from '@/components/map/MapScreen'
import { PageShell } from '@/components/ui/PageShell'
import {
  buildMapNavigationHrefAfterRegionClear,
  getMapUnsupportedRegionCode,
} from '@/lib/map/map-navigation'
import { buildMapPointsQueryKey } from '@/lib/map/map-points-query'
import { fetchMapPointsOnServer } from '@/lib/map/map-points-server'
import { fetchCreators, toSingleValue, type RawSearchParams } from '@/lib/restaurants-api'

import styles from '@/components/map/MapScreen.module.css'

type MapPageProps = {
  searchParams: Promise<RawSearchParams>
}

const MAP_FILTER_KEYS = ['query', 'district', 'category', 'creatorId'] as const

function buildMapNavigationParams(rawParams: RawSearchParams): URLSearchParams {
  const params = new URLSearchParams()
  for (const key of MAP_FILTER_KEYS) {
    const value = toSingleValue(rawParams[key])?.trim()
    if (value) {
      params.set(key, value)
    }
  }
  return params
}

/*
 * 지도 화면의 이름·자치구·음식 카테고리·유튜버 조건만 URL 쿼리로 공유 가능하게 읽는다.
 * 지도 뷰포트는 이 화면과 서버 요청 어디에도 전달하지 않는 Kakao 지도 전용 표시 상태다
 * (ADR-WEB-002, ADR-MAP-001 4.2~4.4).
 */
export default async function MapPage({ searchParams }: MapPageProps) {
  const rawParams = await searchParams
  const navigationParams = buildMapNavigationParams(rawParams)
  const selectedRegionCode = getMapUnsupportedRegionCode(rawParams.regionCode)

  if (selectedRegionCode) {
    return (
      <PageShell
        className={styles.screen}
        eyebrow="지역 기반 탐색"
        title="지도 탐색"
        description="지도는 기존 서울 자치구 조건만 지원합니다. 전국 지역은 맛집 목록에서 탐색할 수 있습니다."
      >
        <p className={styles.notice} role="status">
          선택한 지역은 현재 지도에서 지원하지 않습니다.{' '}
          <Link href={buildMapNavigationHrefAfterRegionClear('/map', navigationParams)}>
            지역 필터를 해제하고 지도 이용하기
          </Link>
        </p>
      </PageShell>
    )
  }

  const [requestHeaders, creatorsResult] = await Promise.all([headers(), fetchCreators()])
  const trustedClientAddress = requestHeaders.get('x-masiton-client-ip') ?? undefined

  const initialFilters = {
    query: toSingleValue(rawParams.query)?.trim() || undefined,
    district: toSingleValue(rawParams.district) || undefined,
    category: toSingleValue(rawParams.category) || undefined,
    creatorId: toSingleValue(rawParams.creatorId) || undefined,
  }

  /*
   * ADR-WEB-002: 최초 응답에 실제 지도 결과가 있도록 MapScreen의 client useQuery가
   * 처음 렌더링에서 만드는 것과 정확히 같은 조건(initialFilters)으로 서버에서 미리
   * 조회해 hydrate한다. queryKey는 buildMapPointsQueryKey 하나로 MapScreen.tsx와
   * 공유하므로 두 곳을 손으로 맞출 필요가 없다.
   */
  const queryClient = new QueryClient()
  await queryClient.prefetchQuery({
    queryKey: buildMapPointsQueryKey(initialFilters),
    queryFn: () => fetchMapPointsOnServer(initialFilters, trustedClientAddress),
  })
  const dehydratedState = dehydrate(queryClient)

  return (
    <HydrationBoundary state={dehydratedState}>
      <MapScreen initialFilters={initialFilters} creatorsResult={creatorsResult} />
    </HydrationBoundary>
  )
}
