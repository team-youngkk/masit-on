import Link from 'next/link'

import { FavoriteButton } from '@/components/personal/FavoriteButton'
import { NaturalLanguageRestaurantSearch } from '@/components/restaurants/NaturalLanguageRestaurantSearch'
import { Button } from '@/components/ui/Button'
import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'
import { cn } from '@/lib/cn'
import { buildMapNavigationHref } from '@/lib/map/map-navigation'
import { naturalLanguageFiltersKey } from '@/lib/natural-language-filters-key'
import {
  buildRestaurantFilterClearHref,
  buildRestaurantFiltersResetHref,
  type RestaurantStructuredFilterKey,
} from '@/lib/restaurants-filter-navigation'
import {
  CATEGORY_OPTIONS,
  DISTRICT_OPTIONS,
  buildApiSearchParams,
  buildPageNumbers,
  buildRestaurantsHref,
  fetchCreators,
  fetchRestaurants,
  toSingleValue,
  type RawSearchParams,
} from '@/lib/restaurants-api'

import styles from './restaurants.module.css'

type RestaurantsPageProps = { searchParams: Promise<RawSearchParams> }
type ActiveFilter = {
  key: RestaurantStructuredFilterKey
  label: string
  value: string
}

/* 검색·필터·페이지 상태는 URL 쿼리로만 관리한다(PRD 8절, pagination-contract.md). */
export default async function RestaurantsPage({
  searchParams,
}: RestaurantsPageProps) {
  const rawParams = await searchParams
  const apiParams = buildApiSearchParams(rawParams)
  const [result, creatorsResult] = await Promise.all([
    fetchRestaurants(apiParams),
    fetchCreators(),
  ])

  /* 반복 URL 값은 API 요청과 같은 규칙으로 첫 값만 사용한다. */
  const currentQuery = toSingleValue(rawParams.query) ?? ''
  const currentDistrict = toSingleValue(rawParams.district) ?? ''
  const currentCategory = toSingleValue(rawParams.category) ?? ''
  const currentCreatorId = toSingleValue(rawParams.creatorId)
  const currentSize = apiParams.get('size') ?? '20'
  const currentRoute = `/restaurants?${apiParams.toString()}`
  const mapHref = buildMapNavigationHref('/restaurants', apiParams)
  const currentCreatorKnown =
    creatorsResult.ok &&
    (!currentCreatorId ||
      creatorsResult.data.items.some(
        (creator) => creator.id === currentCreatorId,
      ))

  /* 이 4개만 URL이 소유한 직접 필터다. page/size/tags는 포함하지 않는다. */
  const naturalLanguageFilters = {
    query: currentQuery.trim() || null,
    district: currentDistrict || null,
    category: currentCategory || null,
    creatorId: currentCreatorId ?? null,
    tags: [],
  }
  const activeFilters: ActiveFilter[] = [
    ...(currentQuery.trim()
      ? [{ key: 'query' as const, label: '검색어', value: currentQuery.trim() }]
      : []),
    ...(currentDistrict
      ? [{ key: 'district' as const, label: '지역', value: currentDistrict }]
      : []),
    ...(currentCategory
      ? [
          {
            key: 'category' as const,
            label: '음식 종류',
            value: currentCategory,
          },
        ]
      : []),
    ...(currentCreatorId
      ? [
          {
            key: 'creatorId' as const,
            label: '유튜버',
            value: creatorsResult.ok
              ? (creatorsResult.data.items.find(
                  (creator) => creator.id === currentCreatorId,
                )?.channelName ?? '선택할 수 없는 유튜버')
              : '선택한 유튜버',
          },
        ]
      : []),
  ]
  const items = result.ok ? result.data.items : []
  const page = result.ok ? result.data.page : null
  const pageNumbers = page ? buildPageNumbers(page.number, page.totalPages) : []

  return (
    <PageShell className={styles.page}>
      <section className={styles.hero} aria-labelledby="restaurants-title">
        <p className={styles.eyebrow}>맛집 탐색</p>
        <h1 id="restaurants-title">유튜버가 다녀온 진짜 맛집</h1>
        <p>이름, 지역, 음식 종류, 유튜버로 원하는 맛집을 찾아보세요.</p>
      </section>

      <form
        id="structured-restaurant-search"
        method="get"
        className={styles.structuredSearch}
        aria-label="맛집 검색 조건"
      >
        <div className={styles.nameSearch}>
          <label className={styles.srOnly} htmlFor="restaurant-query">
            맛집 이름 검색
          </label>
          <input
            id="restaurant-query"
            name="query"
            defaultValue={currentQuery}
            placeholder="맛집 이름을 검색하세요"
            maxLength={100}
            className={styles.queryInput}
          />
          <Button type="submit" className={styles.querySubmit}>
            검색
          </Button>
        </div>
        <div className={styles.toolbarWrap}>
          <div className={styles.toolbar}>
            <div className={styles.filterControls}>
              <label className={styles.selectLabel} htmlFor="district">
                <span className={styles.srOnly}>지역</span>
                <select
                  id="district"
                  name="district"
                  defaultValue={currentDistrict}
                  className={styles.select}
                >
                  <option value="">지역</option>
                  {DISTRICT_OPTIONS.map((district) => (
                    <option key={district} value={district}>
                      {district}
                    </option>
                  ))}
                </select>
              </label>
              <label className={styles.selectLabel} htmlFor="category">
                <span className={styles.srOnly}>음식 종류</span>
                <select
                  id="category"
                  name="category"
                  defaultValue={currentCategory}
                  className={styles.select}
                >
                  <option value="">음식 종류</option>
                  {CATEGORY_OPTIONS.map((category) => (
                    <option key={category} value={category}>
                      {category}
                    </option>
                  ))}
                </select>
              </label>
              <label className={styles.selectLabel} htmlFor="creatorId">
                <span className={styles.srOnly}>유튜버</span>
                {creatorsResult.ok ? (
                  <select
                    id="creatorId"
                    name="creatorId"
                    defaultValue={currentCreatorId ?? ''}
                    className={styles.select}
                  >
                    <option value="">유튜버</option>
                    {creatorsResult.data.items.map((creator) => (
                      <option key={creator.id} value={creator.id}>
                        {creator.channelName}
                      </option>
                    ))}
                    {!currentCreatorKnown && currentCreatorId ? (
                      <option value={currentCreatorId}>
                        선택할 수 없는 유튜버
                      </option>
                    ) : null}
                  </select>
                ) : (
                  <>
                    <select
                      id="creatorId"
                      defaultValue=""
                      className={styles.select}
                      disabled
                    >
                      <option value="">유튜버</option>
                    </select>
                    {currentCreatorId ? (
                      <input
                        type="hidden"
                        name="creatorId"
                        value={currentCreatorId}
                      />
                    ) : null}
                  </>
                )}
              </label>
            </div>
            <Link
              href={buildRestaurantFiltersResetHref(apiParams)}
              className={styles.resetLink}
            >
              필터 초기화
            </Link>
          </div>
          {activeFilters.length > 0 ? (
            <div className={styles.chipStrip} aria-label="적용된 검색 조건">
              {activeFilters.map((filter) => (
                <Link
                  key={filter.key}
                  href={buildRestaurantFilterClearHref(apiParams, filter.key)}
                  className={styles.filterChip}
                  aria-label={`${filter.label} ${filter.value} 필터 해제`}
                >
                  {filter.value} <span aria-hidden="true">×</span>
                </Link>
              ))}
            </div>
          ) : null}
          {!creatorsResult.ok ? (
            <p className={styles.creatorError} role="alert">
              {creatorsResult.message}
              {creatorsResult.traceId ? (
                <span className={styles.traceId}>
                  traceId: {creatorsResult.traceId}
                </span>
              ) : null}
              {currentCreatorId ? (
                <Link
                  href={buildRestaurantFilterClearHref(apiParams, 'creatorId')}
                >
                  유튜버 필터 해제
                </Link>
              ) : null}
            </p>
          ) : creatorsResult.data.items.length === 0 ? (
            <p className={styles.creatorHint}>등록된 유튜버가 없습니다.</p>
          ) : null}
        </div>
        <input type="hidden" name="size" value={currentSize} />
      </form>

      <NaturalLanguageRestaurantSearch
        key={naturalLanguageFiltersKey(naturalLanguageFilters)}
        structuredFormId="structured-restaurant-search"
        creatorLabels={
          creatorsResult.ok
            ? Object.fromEntries(
                creatorsResult.data.items.map((creator) => [
                  creator.id,
                  creator.channelName,
                ]),
              )
            : {}
        }
        filters={naturalLanguageFilters}
        returnTo={currentRoute}
      />

      {!result.ok ? (
        <StatePanel
          title="맛집을 불러올 수 없습니다"
          description={result.message}
          tone="danger"
          traceId={result.traceId}
        />
      ) : items.length === 0 ? (
        <StatePanel
          title="조건에 맞는 맛집이 없습니다"
          description="검색어 또는 필터를 바꿔 다시 찾아보세요."
          compact
        />
      ) : (
        <>
          <section
            className={styles.resultHeader}
            aria-labelledby="results-title"
          >
            <h2 id="results-title">
              검색 결과 {page?.totalElements ?? items.length}곳
            </h2>
            <span
              className={styles.staticSort}
              aria-label="정렬 기준: 기본 정렬, 이름순"
            >
              기본 정렬 · 이름순
            </span>
          </section>
          <ul className={styles.list}>
            {items.map((restaurant) => (
              <li key={restaurant.id} className={styles.listItem}>
                <article className={styles.restaurantCard}>
                  <div className={styles.cardHeading}>
                    <div>
                      <p className={styles.cardMeta}>
                        {restaurant.district} · {restaurant.category}
                      </p>
                      <h3>{restaurant.name}</h3>
                    </div>
                    <FavoriteButton
                      restaurantId={restaurant.id}
                      restaurantName={restaurant.name}
                      returnTo={currentRoute}
                    />
                  </div>
                  {restaurant.visitedBy.length > 0 ? (
                    <p className={styles.visitedBy}>
                      방문 유튜버:{' '}
                      {restaurant.visitedBy
                        .map((creator) => creator.channelName)
                        .join(', ')}
                      {restaurant.remainingVisitedByCount > 0
                        ? ` 외 ${restaurant.remainingVisitedByCount}명`
                        : ''}
                    </p>
                  ) : (
                    <p className={styles.visitedBy}>
                      방문 유튜버 정보가 없습니다.
                    </p>
                  )}
                  <Link
                    href={`/restaurants/${encodeURIComponent(restaurant.id)}`}
                    className={styles.detailLink}
                    aria-label={`${restaurant.name} 상세 보기`}
                  >
                    상세 보기 <span aria-hidden="true">→</span>
                  </Link>
                </article>
              </li>
            ))}
          </ul>
          <aside className={styles.helperPanel} aria-label="맛집 탐색 도움말">
            <div>
              <strong>원하는 맛집이 보이지 않나요?</strong>
              <p>검색어나 필터를 바꾸거나 지도에서 주변 맛집을 찾아보세요.</p>
            </div>
            <Link href={mapHref} className={styles.mapLink}>
              지도 보기 <span aria-hidden="true">↗</span>
            </Link>
          </aside>
          {page ? (
            <nav className={styles.pagination} aria-label="페이지 이동">
              <p className={styles.pageStatus}>
                {page.number} / {Math.max(page.totalPages, 1)} 페이지 (총{' '}
                {page.totalElements}건)
              </p>
              <div className={styles.pageLinks}>
                {page.number > 1 ? (
                  <Link
                    href={buildRestaurantsHref(apiParams, page.number - 1)}
                    className={styles.pageLink}
                  >
                    이전
                  </Link>
                ) : (
                  <span
                    className={cn(styles.pageLink, styles.disabled)}
                    aria-disabled="true"
                  >
                    이전
                  </span>
                )}
                {pageNumbers.map((pageNumber) =>
                  pageNumber === page.number ? (
                    <span
                      key={pageNumber}
                      className={cn(styles.pageLink, styles.current)}
                      aria-current="page"
                    >
                      {pageNumber}
                    </span>
                  ) : (
                    <Link
                      key={pageNumber}
                      href={buildRestaurantsHref(apiParams, pageNumber)}
                      className={styles.pageLink}
                    >
                      {pageNumber}
                    </Link>
                  ),
                )}
                {page.hasNext ? (
                  <Link
                    href={buildRestaurantsHref(apiParams, page.number + 1)}
                    className={styles.pageLink}
                  >
                    다음
                  </Link>
                ) : (
                  <span
                    className={cn(styles.pageLink, styles.disabled)}
                    aria-disabled="true"
                  >
                    다음
                  </span>
                )}
              </div>
            </nav>
          ) : null}
        </>
      )}
    </PageShell>
  )
}
