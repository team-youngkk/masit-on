import { notFound } from 'next/navigation'

import { PageShell, SectionHeader } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'
import { isSafeHttpUrl } from '@/lib/api'
import {
  CreatorDetailUnavailableError,
  CreatorIdentifierInvalidError,
  CreatorNotFoundError,
  fetchCreatorRestaurants,
  fetchCreatorVideos,
  getCreatorDetail,
  parsePageParam,
  type CreatorDetail,
} from '@/lib/creators-api'
import { toSingleValue, type RawSearchParams } from '@/lib/restaurants-api'

import { CreatorRestaurantsSection } from './CreatorRestaurantsSection'
import { CreatorVideosSection } from './CreatorVideosSection'
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
 *
 * 이 서버 렌더는 두 목록의 최초 페이지까지만 담당한다. 이후 페이지 이동·재시도는
 * 각 목록의 클라이언트 경계가 자기 endpoint만 조회한다. searchParams가 바뀌면 이
 * 함수 전체가 다시 실행되어 상대 목록까지 재요청되기 때문이다
 * (creator-detail-api.md 2절: 한 목록의 페이지 이동이 다른 목록을 다시 요청하지 않는다).
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
      <PageShell title="유튜버 상세">
        <StatePanel
          title="유튜버 정보를 불러올 수 없습니다"
          description="일시적으로 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요."
          tone="danger"
          traceId={traceId}
        />
      </PageShell>
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
    <PageShell>
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
        <SectionHeader title="방문 맛집" />
        <CreatorRestaurantsSection
          creatorId={id}
          initialPage={restaurantsPage}
          initialResult={restaurantsResult}
        />
      </section>

      <section className={styles.listSection} aria-label="근거 영상">
        <SectionHeader title="근거 영상" />
        <CreatorVideosSection
          creatorId={id}
          initialPage={videosPage}
          initialResult={videosResult}
        />
      </section>
    </article>
    </PageShell>
  )
}
