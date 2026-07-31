'use client'

import { useCallback, useState } from 'react'

import { Card } from '@/components/ui/Card'
import { isSafeHttpUrl } from '@/lib/api'
import type { CreatorVideosResponse, FetchCreatorVideosResult } from '@/lib/creators-api'
import {
  loadCreatorListPage,
  nextCreatorListSearch,
} from '@/lib/creators/creator-list-navigation'

import { CreatorPageNav } from './CreatorPageNav'
import styles from './page.module.css'

const FALLBACK_MESSAGE =
  '근거 영상을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

/*
 * API-CREATOR-DETAIL-003 근거 영상 영역. 방문 맛집 영역과 상태·조회를 공유하지 않아
 * 한쪽의 페이지 이동·재시도가 다른 목록을 재요청하지 않는다
 * (creator-detail-api.md 2절, PRD-DETAIL-002 5.3절).
 */
export function CreatorVideosSection({
  creatorId,
  initialPage,
  initialResult,
}: {
  creatorId: string
  initialPage: number
  initialResult: FetchCreatorVideosResult
}) {
  const [page, setPage] = useState(initialPage)
  const [result, setResult] = useState(initialResult)
  const [pending, setPending] = useState(false)

  const load = useCallback(
    async (nextPage: number) => {
      setPending(true)
      const loaded = await loadCreatorListPage<CreatorVideosResponse>(
        creatorId,
        'videos',
        nextPage,
        FALLBACK_MESSAGE,
      )
      setResult(loaded)
      setPage(nextPage)
      setPending(false)

      const search = nextCreatorListSearch(window.location.search, 'videos', nextPage)
      window.history.replaceState(null, '', `${window.location.pathname}?${search}`)
    },
    [creatorId],
  )

  if (!result.ok) {
    return (
      <div className={styles.sectionError} role="alert">
        <p>{result.message}</p>
        {result.traceId ? (
          <p className={styles.traceId}>traceId: {result.traceId}</p>
        ) : null}
        <button
          type="button"
          className={styles.retryLink}
          disabled={pending}
          onClick={() => void load(page)}
        >
          다시 시도
        </button>
      </div>
    )
  }

  const { items, page: pageInfo } = result.data

  if (items.length === 0) {
    return (
      <>
        <p className={styles.emptyState}>공개된 근거 영상이 없습니다.</p>
        {pageInfo.totalElements > 0 ? (
          <CreatorPageNav
            page={pageInfo}
            pending={pending}
            onMove={(nextPage) => void load(nextPage)}
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
        page={pageInfo}
        pending={pending}
        onMove={(nextPage) => void load(nextPage)}
      />
    </>
  )
}
