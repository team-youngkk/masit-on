import { notFound } from 'next/navigation'
import Link from 'next/link'
import { cache } from 'react'
import type { Metadata } from 'next'

import { Card } from '@/components/ui/Card'
import { PageShell, SectionHeader } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'
import { FavoriteButton } from '@/components/personal/FavoriteButton'
import { CollectionAddControl } from '@/components/personal/CollectionAddControl'
import { RecentViewRecorder } from '@/components/personal/RecentViewRecorder'
import {
  RestaurantDetailUnavailableError,
  RestaurantIdentifierInvalidError,
  RestaurantNotFoundError,
  getRestaurantDetail,
  isSafeHttpUrl,
  type RestaurantDetail,
} from '@/lib/api'
import { toPublicSiteUrl } from '@/lib/site-url'

import styles from './page.module.css'

type RestaurantDetailPageProps = {
  params: Promise<{ id: string }>
}

const getRestaurant = cache(getRestaurantDetail)

export async function generateMetadata({
  params,
}: RestaurantDetailPageProps): Promise<Metadata> {
  const { id } = await params
  const canonical = toPublicSiteUrl(`/restaurants/${encodeURIComponent(id)}`)
  if (!canonical) {
    return { robots: { index: false, follow: false } }
  }

  try {
    const restaurant = await getRestaurant(id)
    const name = restaurant.name.trim()
    const category = restaurant.category.trim()
    if (!name || !category) {
      return { robots: { index: false, follow: false } }
    }

    return {
      title: `${name} | ${category} 맛집 | 맛잇온`,
      description: `유튜버가 방문한 ${category} 맛집 ${name}의 정보를 확인하세요.`,
      robots: { index: true, follow: true },
      alternates: { canonical },
    }
  } catch {
    return { robots: { index: false, follow: false } }
  }
}

/*
 * PRD-DETAIL-001 / API-DETAIL-001.
 * ADR-WEB-002에 따라 초기 서버 데이터는 Server Component `fetch`로 가져온다.
 */
export default async function RestaurantDetailPage({
  params,
}: RestaurantDetailPageProps) {
  const { id } = await params

  let restaurant: RestaurantDetail
  try {
    restaurant = await getRestaurant(id)
  } catch (error) {
    /*
     * 식별자 형식 오류도 찾을 수 없음으로 다뤄 404를 응답한다. 일시적 조회 실패로
     * 보여주면 문구가 원인과 다르고, 없는 자원에 200을 응답해 오류 화면만 그리는
     * 상태가 된다.
     */
    if (
      error instanceof RestaurantNotFoundError ||
      error instanceof RestaurantIdentifierInvalidError
    ) {
      notFound()
    }

    /*
     * 맛집 기본 정보 제공자 실패(500, 네트워크 오류 등)는 찾을 수 없음과
     * 다른 상태다. API-DETAIL-001 8절: 기본 정보 실패는 상세 전체 실패로 다룬다.
     * 이 상태만 서버에서 추적할 원인이 있으므로 traceId를 함께 보여준다.
     */
    const traceId =
      error instanceof RestaurantDetailUnavailableError ? error.traceId : undefined

    return (
      <PageShell title="맛집 상세">
        <StatePanel
          title="맛집 정보를 불러올 수 없습니다"
          description="일시적으로 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요."
          tone="danger"
          traceId={traceId}
        />
      </PageShell>
    )
  }

  return (
    <PageShell>
    <article className={styles.page}>
      <RecentViewRecorder restaurantId={restaurant.id} />
      <header className={styles.header}>
        <div className={styles.heading}>
          <h1 className={styles.name}>{restaurant.name}</h1>
          <p className={styles.category}>{restaurant.category}</p>
        </div>
        <div className={styles.personalActions}>
          <FavoriteButton
            restaurantId={restaurant.id}
            restaurantName={restaurant.name}
            returnTo={`/restaurants/${encodeURIComponent(restaurant.id)}`}
          />
          <CollectionAddControl
            restaurantId={restaurant.id}
            returnTo={`/restaurants/${encodeURIComponent(restaurant.id)}`}
          />
          <Link
            className={styles.reportLink}
            href={`/me/requests/new?kind=report&targetType=RESTAURANT&targetId=${encodeURIComponent(restaurant.id)}`}
          >
            정보 오류 제보
          </Link>
        </div>
      </header>

      <section className={styles.infoSection} aria-label="기본 정보">
        <dl className={styles.infoList}>
          <div className={styles.infoRow}>
            <dt>주소</dt>
            <dd>
              {restaurant.address.roadAddress}
              {restaurant.address.detailAddress != null ? (
                <span className={styles.detailAddress}>
                  {restaurant.address.detailAddress}
                </span>
              ) : null}
            </dd>
          </div>
          <div className={styles.infoRow}>
            <dt>전화번호</dt>
            <dd>{restaurant.phoneNumber}</dd>
          </div>
          <div className={styles.infoRow}>
            <dt>카카오 장소</dt>
            <dd>
              {isSafeHttpUrl(restaurant.kakaoPlaceUrl) ? (
                <a
                  href={restaurant.kakaoPlaceUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  카카오맵에서 보기
                </a>
              ) : (
                restaurant.kakaoPlaceUrl
              )}
            </dd>
          </div>
        </dl>
      </section>

      <section className={styles.contentSection} aria-label="방문 콘텐츠">
        <SectionHeader title="방문 콘텐츠" />
        <RestaurantContent restaurant={restaurant} />
      </section>
    </article>
    </PageShell>
  )
}

function RestaurantContent({ restaurant }: { restaurant: RestaurantDetail }) {
  /*
   * contentStatus가 정상 빈 콘텐츠와 제공자 실패를 구분한다(API-DETAIL-001 8절).
   * 두 상태는 서로 다른 문구로 보여줘야 한다.
   */
  if (restaurant.contentStatus === 'TEMPORARILY_UNAVAILABLE') {
    return (
      <p className={styles.notice}>콘텐츠를 일시적으로 불러올 수 없습니다.</p>
    )
  }

  if (restaurant.visitedBy.length === 0 && restaurant.videos.length === 0) {
    return <p className={styles.notice}>아직 등록된 방문 콘텐츠가 없습니다.</p>
  }

  return (
    <div className={styles.contentGrid}>
      {restaurant.visitedBy.length > 0 ? (
        <div>
          <h3 className={styles.subTitle}>방문 유튜버</h3>
          <ul className={styles.creatorList}>
            {restaurant.visitedBy.map((creator) => (
              <li key={creator.id}>
                <Link href={`/creators/${encodeURIComponent(creator.id)}`}>
                  {creator.channelName}
                </Link>
                {isSafeHttpUrl(creator.channelUrl) ? (
                  <a
                    href={creator.channelUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className={styles.externalCreatorLink}
                  >
                    YouTube 채널
                  </a>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {restaurant.videos.length > 0 ? (
        <div>
          <h3 className={styles.subTitle}>관련 영상</h3>
          <div className={styles.videoGrid}>
            {restaurant.videos.map((video) => {
              /*
               * 썸네일은 관리자가 등록한 외부 YouTube URL이라 도메인이
               * 고정돼 있지 않다. next/image는 next.config.ts에 remotePatterns
               * 등록이 필요해 이 작업 범위(설정 파일 변경 금지) 밖이므로
               * 일반 img 태그를 사용한다.
               */
              const thumbnail = (
                <img
                  src={video.thumbnailUrl}
                  alt={video.title}
                  loading="lazy"
                  decoding="async"
                  className={styles.thumbnail}
                />
              )

              return (
                <Card
                  key={video.id}
                  title={video.title}
                  level={4}
                  meta={video.channelName}
                >
                  {isSafeHttpUrl(video.sourceUrl) ? (
                    <a
                      href={video.sourceUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className={styles.videoLink}
                    >
                      {thumbnail}
                      <span className={styles.videoLinkLabel}>
                        원본 영상 보기
                      </span>
                    </a>
                  ) : (
                    <div className={styles.videoLink}>
                      {thumbnail}
                      <span className={styles.videoLinkLabel}>
                        {video.sourceUrl}
                      </span>
                    </div>
                  )}
                </Card>
              )
            })}
          </div>
        </div>
      ) : null}
    </div>
  )
}
