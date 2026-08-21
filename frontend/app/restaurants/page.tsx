import Link from 'next/link'

import { FavoriteButton } from '@/components/personal/FavoriteButton'
import { FilterSelect } from '@/components/restaurants/FilterSelect'
import { NaturalLanguageRestaurantSearch } from '@/components/restaurants/NaturalLanguageRestaurantSearch'
import { Button } from '@/components/ui/Button'
import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'
import { cn } from '@/lib/cn'
import { shouldUseRestaurantDesignPreview } from '@/lib/design-preview'
import { naturalLanguageFiltersKey } from '@/lib/natural-language-filters-key'
import { getRestaurantPlaceholderImage } from '@/lib/restaurant-placeholder-image'
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
  type RestaurantListItem,
} from '@/lib/restaurants-api'

import styles from './restaurants.module.css'

type RestaurantsPageProps = { searchParams: Promise<RawSearchParams> }
type ActiveFilter = {
  key: RestaurantStructuredFilterKey
  label: string
  value: string
}

const DESIGN_PREVIEW_ITEMS: RestaurantListItem[] = [
  {
    id: 'design-preview-1',
    name: '연남동 진짜곱창',
    district: '마포구',
    category: '곱창',
    visitedBy: [
      { id: 'preview-sungsik', channelName: '성시경' },
      { id: 'preview-baek', channelName: '백종원' },
      { id: 'preview-jjayang', channelName: '쯔양' },
    ],
    remainingVisitedByCount: 0,
  },
  {
    id: 'design-preview-2',
    name: '홍대 멘야하루',
    district: '마포구',
    category: '라멘',
    visitedBy: [{ id: 'preview-baek', channelName: '백종원' }],
    remainingVisitedByCount: 0,
  },
  {
    id: 'design-preview-3',
    name: '성수동 우마카세',
    district: '성동구',
    category: '이자카야',
    visitedBy: [
      { id: 'preview-jjayang', channelName: '쯔양' },
      { id: 'preview-kwak', channelName: '곽튜브' },
    ],
    remainingVisitedByCount: 0,
  },
  {
    id: 'design-preview-4',
    name: '이태원 소담순두부',
    district: '용산구',
    category: '순두부찌개',
    visitedBy: [{ id: 'preview-lee', channelName: '이밥' }],
    remainingVisitedByCount: 0,
  },
  {
    id: 'design-preview-5',
    name: '방이동 평양집',
    district: '송파구',
    category: '냉면',
    visitedBy: [{ id: 'preview-kwak', channelName: '곽튜브' }],
    remainingVisitedByCount: 0,
  },
  {
    id: 'design-preview-6',
    name: '서래마을 오스테리아',
    district: '서초구',
    category: '파스타',
    visitedBy: [{ id: 'preview-choi', channelName: '최자로드' }],
    remainingVisitedByCount: 0,
  },
  {
    id: 'design-preview-7',
    name: '명동 만리장성',
    district: '중구',
    category: '중식',
    visitedBy: [{ id: 'preview-baek', channelName: '백종원' }],
    remainingVisitedByCount: 0,
  },
  {
    id: 'design-preview-8',
    name: '노량진 바다식당',
    district: '동작구',
    category: '해산물',
    visitedBy: [{ id: 'preview-jjayang', channelName: '쯔양' }],
    remainingVisitedByCount: 0,
  },
  {
    id: 'design-preview-9',
    name: '연희동 작은 디저트',
    district: '서대문구',
    category: '디저트',
    visitedBy: [{ id: 'preview-sungsik', channelName: '성시경' }],
    remainingVisitedByCount: 0,
  },
  {
    id: 'design-preview-10',
    name: '망원 커피하우스',
    district: '마포구',
    category: '카페',
    visitedBy: [{ id: 'preview-kwak', channelName: '곽튜브' }],
    remainingVisitedByCount: 0,
  },
]

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
  const isDesignPreview = shouldUseRestaurantDesignPreview({
    nodeEnv: process.env.NODE_ENV,
    previewFlag: process.env.MASITON_UI_PREVIEW,
    hasItems: items.length > 0,
    query: currentQuery,
    district: currentDistrict,
    category: currentCategory,
    creatorId: currentCreatorId,
  })
  const displayItems: RestaurantListItem[] = isDesignPreview
    ? DESIGN_PREVIEW_ITEMS
    : items
  const previewApiWarning = isDesignPreview && !result.ok
  const page = result.ok ? result.data.page : null
  const pageNumbers = page ? buildPageNumbers(page.number, page.totalPages) : []

  return (
    <PageShell className={styles.page}>
      <section className={styles.heroShell}>
        <section className={styles.hero} aria-labelledby="restaurants-title">
          <div className={styles.heroCopy}>
            <p className={styles.eyebrow}>맛집 탐색</p>
            <h1 id="restaurants-title">
              유튜버가 다녀온 맛집,<br />
              <strong>오늘은 어디로 갈까요?</strong>
            </h1>
            <p>검증된 맛집을 영상으로 확인하고, 실패 없는 선택을 즐겨보세요.</p>
          </div>
          <div className={styles.heroVisual} aria-hidden="true">
            <img
              src="/images/restaurant-hero-mascot.png"
              alt=""
              className={styles.heroImage}
            />
          </div>
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
                <FilterSelect
                  id="district"
                  formId="structured-restaurant-search"
                  name="district"
                  options={DISTRICT_OPTIONS.map((district) => ({
                    value: district,
                    label: district,
                  }))}
                  value={currentDistrict}
                  placeholder="지역"
                  className={styles.select}
                  controlClassName={styles.selectControl}
                  menuClassName={styles.selectMenu}
                  optionClassName={styles.selectOption}
                  selectedOptionClassName={styles.selectOptionSelected}
                />
              </label>
              <label className={styles.selectLabel} htmlFor="category">
                <span className={styles.srOnly}>음식 종류</span>
                <FilterSelect
                  id="category"
                  formId="structured-restaurant-search"
                  name="category"
                  options={CATEGORY_OPTIONS.map((category) => ({
                    value: category,
                    label: category,
                  }))}
                  value={currentCategory}
                  placeholder="음식 종류"
                  className={styles.select}
                  controlClassName={styles.selectControl}
                  menuClassName={styles.selectMenu}
                  optionClassName={styles.selectOption}
                  selectedOptionClassName={styles.selectOptionSelected}
                />
              </label>
              <label className={styles.selectLabel} htmlFor="creatorId">
                <span className={styles.srOnly}>유튜버</span>
                {creatorsResult.ok ? (
                  <FilterSelect
                    id="creatorId"
                    formId="structured-restaurant-search"
                    name="creatorId"
                    options={creatorsResult.data.items.map((creator) => ({
                      value: creator.id,
                      label: creator.channelName,
                    }))}
                    value={currentCreatorId ?? ''}
                    placeholder="유튜버"
                    className={styles.select}
                    controlClassName={styles.selectControl}
                    menuClassName={styles.selectMenu}
                    optionClassName={styles.selectOption}
                    selectedOptionClassName={styles.selectOptionSelected}
                  />
                ) : (
                  <>
                    <FilterSelect
                      id="creatorId"
                      formId="structured-restaurant-search"
                      name="creatorId"
                      options={[]}
                      value=""
                      submittedValue={currentCreatorId ?? ''}
                      placeholder="유튜버"
                      disabled
                      className={styles.select}
                      controlClassName={styles.selectControl}
                      menuClassName={styles.selectMenu}
                      optionClassName={styles.selectOption}
                      selectedOptionClassName={styles.selectOptionSelected}
                    />
                  </>
                )}
              </label>
            </div>
            {activeFilters.length > 0 ? (
              <Link
                href={buildRestaurantFiltersResetHref(apiParams)}
                className={styles.resetLink}
              >
                필터 초기화
              </Link>
            ) : null}
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
          ) : creatorsResult.data.items.length === 0 && currentCreatorId ? (
            <p className={styles.creatorHint}>등록된 유튜버가 없습니다.</p>
          ) : null}
        </div>
        <input type="hidden" name="size" value={currentSize} />
        </form>
      </section>

      {!result.ok && !isDesignPreview ? (
        <StatePanel
          title="맛집을 불러올 수 없습니다"
          description={result.message}
          tone="danger"
          traceId={result.traceId}
        />
      ) : displayItems.length === 0 ? (
        <StatePanel
          title="조건에 맞는 맛집이 없습니다"
          description="검색어 또는 필터를 바꿔 다시 찾아보세요."
          compact
        />
      ) : (
        <>
          {previewApiWarning ? (
            <StatePanel
              compact
              tone="warning"
              title="개발용 디자인 프리뷰"
              description="맛집 API가 연결되지 않아 더미 데이터로 표시하고 있습니다."
            />
          ) : null}
          <section
            className={styles.resultHeader}
            aria-labelledby="results-title"
          >
            <h2 id="results-title">
              {isDesignPreview ? '서울 맛집' : '검색 결과'}{' '}
              {isDesignPreview
                ? `${displayItems.length}곳`
                : `${page?.totalElements ?? displayItems.length}곳`}
            </h2>
            <span
              className={styles.staticSort}
              aria-label="정렬 기준: 기본 정렬, 이름순"
            >
              기본 정렬 · 이름순
            </span>
          </section>
          <ul className={styles.list}>
            {displayItems.map((restaurant) => (
              <li key={restaurant.id} className={styles.listItem}>
                <article className={styles.restaurantCard}>
                  <div className={styles.cardMedia}>
                    <img
                      src={
                        getRestaurantPlaceholderImage(
                          restaurant.id,
                          restaurant.category,
                        ).src
                      }
                      alt=""
                      className={styles.cardMediaImage}
                      loading="lazy"
                      decoding="async"
                    />
                  </div>
                  <div className={styles.cardHeading}>
                    <div className={styles.cardTitleBlock}>
                      <div className={styles.cardTitleRow}>
                        <h3>
                          <Link
                            href={
                              isDesignPreview
                                ? `/restaurants?query=${encodeURIComponent(restaurant.name)}`
                                : `/restaurants/${encodeURIComponent(restaurant.id)}`
                            }
                          >
                            {restaurant.name}
                          </Link>
                        </h3>
                        {isDesignPreview ? (
                          <Link
                            href={`/login?returnTo=${encodeURIComponent(currentRoute)}`}
                            className={styles.previewFavorite}
                            aria-label={`${restaurant.name} 찜하려면 로그인`}
                          >
                            ♡
                          </Link>
                        ) : (
                          <FavoriteButton
                            compact
                            restaurantId={restaurant.id}
                            restaurantName={restaurant.name}
                            returnTo={currentRoute}
                          />
                        )}
                      </div>
                      <p className={styles.cardAddress}>
                        {restaurant.district} · {restaurant.category}
                      </p>
                      {restaurant.visitedBy.length > 0 ? (
                        <div className={styles.cardBadges}>
                          <span className={styles.creatorBadge}>
                            {restaurant.visitedBy.length +
                              restaurant.remainingVisitedByCount ===
                            1
                              ? restaurant.visitedBy[0]?.channelName
                              : `유튜버 ${restaurant.visitedBy.length + restaurant.remainingVisitedByCount}명 방문`}
                          </span>
                        </div>
                      ) : null}
                    </div>
                  </div>
                  {isDesignPreview ? (
                    <Link
                      href={`/restaurants?query=${encodeURIComponent(restaurant.name)}`}
                      className={styles.detailLink}
                      aria-label={`${restaurant.name} 검색 결과 보기`}
                    >
                      상세 보기 <span>→</span>
                    </Link>
                  ) : (
                    <Link
                      href={`/restaurants/${encodeURIComponent(restaurant.id)}`}
                      className={styles.detailLink}
                      aria-label={`${restaurant.name} 상세 보기`}
                    >
                      상세 보기 <span aria-hidden="true">→</span>
                    </Link>
                  )}
                </article>
              </li>
            ))}
          </ul>
          {page && !isDesignPreview ? (
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
    </PageShell>
  )
}
