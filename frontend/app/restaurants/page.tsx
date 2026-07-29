import Link from 'next/link'

import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Field } from '@/components/ui/Field'
import { cn } from '@/lib/cn'
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

type RestaurantsPageProps = {
  searchParams: Promise<RawSearchParams>
}

/*
 * 검색·필터·페이지 상태는 URL 쿼리로만 관리한다(PRD 8절, pagination-contract.md).
 * 폼은 GET 제출로 같은 화면에 새 쿼리를 반영하고, page는 폼에 포함하지 않아
 * 조건 변경 시 자동으로 첫 페이지를 요청한다.
 */
export default async function RestaurantsPage({
  searchParams,
}: RestaurantsPageProps) {
  const rawParams = await searchParams
  const apiParams = buildApiSearchParams(rawParams)
  const [result, creatorsResult] = await Promise.all([
    fetchRestaurants(apiParams),
    fetchCreators(),
  ])

  const currentQuery = toSingleValue(rawParams.query) ?? ''
  const currentDistrict = toSingleValue(rawParams.district) ?? ''
  const currentCategory = toSingleValue(rawParams.category) ?? ''
  const currentCreatorId = toSingleValue(rawParams.creatorId)
  const currentSize = apiParams.get('size') ?? '20'

  const items = result.ok ? result.data.items : []
  const page = result.ok ? result.data.page : null
  const pageNumbers = page ? buildPageNumbers(page.number, page.totalPages) : []

  return (
    <section>
      <h1>맛집 탐색</h1>

      <form method="get" className={styles.filters} aria-label="맛집 검색 조건">
        <Field
          label="맛집 이름"
          name="query"
          defaultValue={currentQuery}
          placeholder="예: 강된장"
          maxLength={100}
        />

        <div className={styles.selectGroup}>
          <label className={styles.selectLabel} htmlFor="district">
            자치구
          </label>
          <select
            id="district"
            name="district"
            defaultValue={currentDistrict}
            className={styles.select}
          >
            <option value="">전체</option>
            {DISTRICT_OPTIONS.map((district) => (
              <option key={district} value={district}>
                {district}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.selectGroup}>
          <label className={styles.selectLabel} htmlFor="category">
            대표 음식
          </label>
          <select
            id="category"
            name="category"
            defaultValue={currentCategory}
            className={styles.select}
          >
            <option value="">전체</option>
            {CATEGORY_OPTIONS.map((category) => (
              <option key={category} value={category}>
                {category}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.selectGroup}>
          <label className={styles.selectLabel} htmlFor="creatorId">
            유튜버
          </label>
          {creatorsResult.ok && creatorsResult.data.items.length > 0 ? (
            <select
              id="creatorId"
              name="creatorId"
              defaultValue={currentCreatorId ?? ''}
              className={styles.select}
            >
              <option value="">전체</option>
              {creatorsResult.data.items.map((creator) => (
                <option key={creator.id} value={creator.id}>
                  {creator.channelName}
                </option>
              ))}
            </select>
          ) : (
            <select id="creatorId" defaultValue="" className={styles.select} disabled>
              <option value="">전체</option>
            </select>
          )}
          {/* select가 비활성화된 두 경우(빈 목록·조회 실패) 모두 기존 선택값을 잃지 않게 보존한다 */}
          {(!creatorsResult.ok || creatorsResult.data.items.length === 0) &&
          currentCreatorId ? (
            <input type="hidden" name="creatorId" value={currentCreatorId} />
          ) : null}
          {creatorsResult.ok && creatorsResult.data.items.length === 0 ? (
            <p className={styles.selectHint}>등록된 유튜버가 없습니다.</p>
          ) : null}
          {!creatorsResult.ok ? (
            <p className={styles.selectError} role="alert">
              {creatorsResult.message}
            </p>
          ) : null}
        </div>

        <input type="hidden" name="size" value={currentSize} />

        <Button type="submit" className={styles.submit}>
          검색
        </Button>
      </form>

      {!result.ok ? (
        <p className={styles.error} role="alert">
          {result.message}
          {result.traceId ? (
            <span className={styles.traceId}>traceId: {result.traceId}</span>
          ) : null}
        </p>
      ) : items.length === 0 ? (
        <p className={styles.state}>조건에 맞는 맛집이 없습니다.</p>
      ) : (
        <>
          <ul className={styles.list}>
            {items.map((restaurant) => (
              <li key={restaurant.id}>
                <Card
                  title={
                    <Link href={`/restaurants/${restaurant.id}`}>
                      {restaurant.name}
                    </Link>
                  }
                  level={2}
                  meta={`${restaurant.district} · ${restaurant.category}`}
                >
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
                  ) : null}
                </Card>
              </li>
            ))}
          </ul>

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
    </section>
  )
}
