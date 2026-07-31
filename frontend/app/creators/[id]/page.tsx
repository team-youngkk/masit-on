import Link from 'next/link'
import { notFound } from 'next/navigation'

import { Card } from '@/components/ui/Card'
import { isSafeHttpUrl } from '@/lib/api'
import { cn } from '@/lib/cn'
import {
  CreatorDetailUnavailableError,
  CreatorIdentifierInvalidError,
  CreatorNotFoundError,
  buildCreatorDetailHref,
  fetchCreatorRestaurants,
  fetchCreatorVideos,
  getCreatorDetail,
  parsePageParam,
  type CreatorDetail,
  type CreatorListPage,
  type FetchCreatorRestaurantsResult,
  type FetchCreatorVideosResult,
} from '@/lib/creators-api'
import { toSingleValue, type RawSearchParams } from '@/lib/restaurants-api'

import styles from './page.module.css'

type CreatorDetailPageProps = {
  params: Promise<{ id: string }>
  searchParams: Promise<RawSearchParams>
}

/*
 * PRD-DETAIL-002 / API-CREATOR-DETAIL-001~003.
 * ADR-WEB-002에 따라 초기 서버 데이터는 Server Component `fetch`로 가져온다.
 * 방문 맛집·근거 영상은 각각 독립된 쿼리 파라미터(restaurantsPage, videosPage)로
 * 페이지 상태를 관리한다(PRD 6·7절: 두 목록을 하나의 공통 페이지 상태로 묶지 않는다).
 */
export default async function CreatorDetailPage({
  params,
  searchParams,
}: CreatorDetailPageProps) {
  const { id } = await params
  const rawParams = await searchParams

  const restaurantsPage = parsePageParam(toSingleValue(rawParams.restaurantsPage))
  const videosPage = parsePageParam(toSingleValue(rawParams.videosPage))

  let creator: CreatorDetail
  try {
    creator = await getCreatorDetail(id)
  } catch (error) {
    /*
     * 식별자 형식 오류도 찾을 수 없음으로 다뤄 404를 응답한다. 없음·비공개·삭제·
     * 외부 이용 불가를 구분해 표시하지 않는다(PRD 9절, API 3절).
     */
    if (
      error instanceof CreatorNotFoundError ||
      error instanceof CreatorIdentifierInvalidError
    ) {
      notFound()
    }

    /*
     * 기본 정보 제공자 실패(500, 네트워크 오류 등)는 찾을 수 없음과 다른 상태다.
     * API-CREATOR-DETAIL-001 9절: 기본 정보 실패는 상세 전체 실패로 다룬다.
     * 이 상태만 서버에서 추적할 원인이 있으므로 traceId를 함께 보여준다.
     */
    const traceId =
      error instanceof CreatorDetailUnavailableError ? error.traceId : undefined

    return (
      <section className={styles.errorState}>
        <h1>유튜버 정보를 불러올 수 없습니다</h1>
        <p>일시적으로 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.</p>
        {traceId ? <p className={styles.traceId}>traceId: {traceId}</p> : null}
      </section>
    )
  }

  /*
   * 두 연결 목록은 서로 다른 API 호출이며 한쪽이 실패해도 채널 정보와
   * 다른 목록은 그대로 유지한다(PRD 9절). 그래서 개별 실패를 throw하지 않는
   * Result 형태로 받는다.
   */
  const [restaurantsResult, videosResult] = await Promise.all([
    fetchCreatorRestaurants(id, restaurantsPage),
    fetchCreatorVideos(id, videosPage),
  ])

  return (
    <article className={styles.page}>
      <header className={styles.header}>
        {creator.profileImageUrl != null && isSafeHttpUrl(creator.profileImageUrl) ? (
          <img
            src={creator.profileImageUrl}
            alt={creator.channelName}
            className={styles.profileImage}
          />
        ) : null}

        <div className={styles.heading}>
          <h1 className={styles.name}>{creator.channelName}</h1>
          {creator.handle != null || creator.description != null ? (
            <p className={styles.subline}>
              {creator.handle != null ? (
                <span className={styles.handle}>{creator.handle}</span>
              ) : null}
              {creator.handle != null && creator.description != null ? (
                <span className={styles.sublineSeparator}> · </span>
              ) : null}
              {creator.description != null ? <span>{creator.description}</span> : null}
            </p>
          ) : null}
        </div>

        {isSafeHttpUrl(creator.channelUrl) ? (
          <a
            href={creator.channelUrl}
            target="_blank"
            rel="noopener noreferrer"
            className={styles.channelLink}
          >
            YouTube 채널
          </a>
        ) : null}
      </header>

      <section className={styles.listSection} aria-label="방문 맛집">
        <h2 className={styles.sectionTitle}>방문 맛집</h2>
        <CreatorRestaurantsSection
          creatorId={id}
          result={restaurantsResult}
          restaurantsPage={restaurantsPage}
          videosPage={videosPage}
        />
      </section>

      <section className={styles.listSection} aria-label="근거 영상">
        <h2 className={styles.sectionTitle}>근거 영상</h2>
        <CreatorVideosSection
          creatorId={id}
          result={videosResult}
          restaurantsPage={restaurantsPage}
          videosPage={videosPage}
        />
      </section>
    </article>
  )
}

function CreatorRestaurantsSection({
  creatorId,
  result,
  restaurantsPage,
  videosPage,
}: {
  creatorId: string
  result: FetchCreatorRestaurantsResult
  restaurantsPage: number
  videosPage: number
}) {
  if (!result.ok) {
    return (
      <div className={styles.sectionError} role="alert">
        <p>{result.message}</p>
        {result.traceId ? (
          <p className={styles.traceId}>traceId: {result.traceId}</p>
        ) : null}
        <Link
          href={buildCreatorDetailHref(creatorId, { restaurantsPage, videosPage })}
          className={styles.retryLink}
        >
          다시 시도
        </Link>
      </div>
    )
  }

  const { items, page } = result.data

  if (items.length === 0) {
    return (
      <>
        <p className={styles.emptyState}>공개된 방문 맛집이 없습니다.</p>
        {page.totalElements > 0 ? (
          <CreatorPageNav
            page={page}
            buildHref={(nextPage) =>
              buildCreatorDetailHref(creatorId, {
                restaurantsPage: nextPage,
                videosPage,
              })
            }
          />
        ) : null}
      </>
    )
  }

  return (
    <>
      <ul className={styles.restaurantList}>
        {items.map((restaurant) => (
          <li key={restaurant.id}>
            <Card
              title={
                <Link href={`/restaurants/${encodeURIComponent(restaurant.id)}`}>
                  {restaurant.name}
                </Link>
              }
              level={3}
              meta={`${restaurant.district} · ${restaurant.category}`}
            />
          </li>
        ))}
      </ul>
      <CreatorPageNav
        page={page}
        buildHref={(nextPage) =>
          buildCreatorDetailHref(creatorId, { restaurantsPage: nextPage, videosPage })
        }
      />
    </>
  )
}

function CreatorVideosSection({
  creatorId,
  result,
  restaurantsPage,
  videosPage,
}: {
  creatorId: string
  result: FetchCreatorVideosResult
  restaurantsPage: number
  videosPage: number
}) {
  if (!result.ok) {
    return (
      <div className={styles.sectionError} role="alert">
        <p>{result.message}</p>
        {result.traceId ? (
          <p className={styles.traceId}>traceId: {result.traceId}</p>
        ) : null}
        <Link
          href={buildCreatorDetailHref(creatorId, { restaurantsPage, videosPage })}
          className={styles.retryLink}
        >
          다시 시도
        </Link>
      </div>
    )
  }

  const { items, page } = result.data

  if (items.length === 0) {
    return (
      <>
        <p className={styles.emptyState}>공개된 근거 영상이 없습니다.</p>
        {page.totalElements > 0 ? (
          <CreatorPageNav
            page={page}
            buildHref={(nextPage) =>
              buildCreatorDetailHref(creatorId, {
                restaurantsPage,
                videosPage: nextPage,
              })
            }
          />
        ) : null}
      </>
    )
  }

  return (
    <>
      <div className={styles.videoGrid}>
        {items.map((video) => {
          /*
           * 썸네일은 저장된 외부 YouTube URL이라 도메인이 고정돼 있지 않다.
           * next/image는 next.config.ts에 remotePatterns 등록이 필요해 이
           * 작업 범위(설정 파일 변경 금지) 밖이므로 일반 img 태그를 사용한다.
           */
          const thumbnail = isSafeHttpUrl(video.thumbnailUrl) ? (
            <img
              src={video.thumbnailUrl}
              alt={video.title}
              className={styles.thumbnail}
            />
          ) : null

          return (
            <Card key={video.id} title={video.title} level={3}>
              {isSafeHttpUrl(video.sourceUrl) ? (
                <a
                  href={video.sourceUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className={styles.videoLink}
                >
                  {thumbnail}
                  <span className={styles.videoLinkLabel}>원본 영상 보기</span>
                </a>
              ) : (
                <div className={styles.videoLink}>
                  {thumbnail}
                  <span className={styles.videoLinkLabel}>{video.sourceUrl}</span>
                </div>
              )}
            </Card>
          )
        })}
      </div>
      <CreatorPageNav
        page={page}
        buildHref={(nextPage) =>
          buildCreatorDetailHref(creatorId, { restaurantsPage, videosPage: nextPage })
        }
      />
    </>
  )
}

/*
 * 와이어프레임 7절 레이아웃(이전/다음)에 맞춰 번호 목록 없이 이전·다음만
 * 제공한다. 총 건수·페이지 수는 상태 인지를 돕기 위해 함께 보여준다.
 */
function CreatorPageNav({
  page,
  buildHref,
}: {
  page: CreatorListPage
  buildHref: (page: number) => string
}) {
  return (
    <nav className={styles.pagination} aria-label="페이지 이동">
      <p className={styles.pageStatus}>
        {page.number} / {Math.max(page.totalPages, 1)} 페이지 (총 {page.totalElements}건)
      </p>
      <div className={styles.pageLinks}>
        {page.number > 1 ? (
          <Link href={buildHref(page.number - 1)} className={styles.pageLink}>
            이전
          </Link>
        ) : (
          <span className={cn(styles.pageLink, styles.disabled)} aria-disabled="true">
            이전
          </span>
        )}

        {page.hasNext ? (
          <Link href={buildHref(page.number + 1)} className={styles.pageLink}>
            다음
          </Link>
        ) : (
          <span className={cn(styles.pageLink, styles.disabled)} aria-disabled="true">
            다음
          </span>
        )}
      </div>
    </nav>
  )
}
