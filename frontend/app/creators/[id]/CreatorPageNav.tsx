'use client'

import { cn } from '@/lib/cn'
import type { CreatorListPage } from '@/lib/creators-api'

import styles from './page.module.css'

/*
 * 와이어프레임 7절 레이아웃(이전/다음)에 맞춰 번호 목록 없이 이전·다음만 제공한다.
 * 링크가 아니라 버튼인 이유: 링크 이동은 searchParams를 바꿔 Server Component 전체를
 * 다시 실행시키고 그 과정에서 상대 목록 API까지 재요청된다
 * (creator-detail-api.md 2절 위반). 이동은 해당 목록의 조회만 수행한다.
 */
export function CreatorPageNav({
  page,
  pending,
  onMove,
}: {
  page: CreatorListPage
  pending: boolean
  onMove: (page: number) => void
}) {
  const hasPrevious = page.number > 1

  return (
    <nav className={styles.pagination} aria-label="페이지 이동">
      <p className={styles.pageStatus} aria-live="polite">
        {page.number} / {Math.max(page.totalPages, 1)} 페이지 (총 {page.totalElements}건)
        {pending ? ' · 불러오는 중' : ''}
      </p>
      <div className={styles.pageLinks}>
        <button
          type="button"
          className={cn(styles.pageLink, !hasPrevious || pending ? styles.disabled : undefined)}
          disabled={!hasPrevious || pending}
          onClick={() => onMove(page.number - 1)}
        >
          이전
        </button>
        <button
          type="button"
          className={cn(styles.pageLink, !page.hasNext || pending ? styles.disabled : undefined)}
          disabled={!page.hasNext || pending}
          onClick={() => onMove(page.number + 1)}
        >
          다음
        </button>
      </div>
    </nav>
  )
}
