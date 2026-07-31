import { MapScreen } from '@/components/map/MapScreen'
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

  return <MapScreen initialFilters={initialFilters} creatorsResult={creatorsResult} />
}
