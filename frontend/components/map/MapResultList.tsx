'use client'

import type { MapPointsViewState } from '@/lib/map/map-points-response'
import { isMapPointSelected } from '@/lib/map/selection-sync'

import styles from './MapResultList.module.css'

type MapResultListProps = {
  view: MapPointsViewState | null
  isLoading: boolean
  /* 결과가 아직 없는 이유가 진짜 빈 영역이 아니라 첫 조회의 오류·호출 제한일 때 true. */
  isBlocked: boolean
  selectedId: string | null
  onSelect: (id: string) => void
  onResetFilters: () => void
}

export function MapResultList({
  view,
  isLoading,
  isBlocked,
  selectedId,
  onSelect,
  onResetFilters,
}: MapResultListProps) {
  if (isLoading) {
    return (
      <section className={styles.list} aria-label="현재 영역 맛집 목록">
        <p className={styles.state} aria-live="polite">
          지도와 목록을 불러오는 중입니다.
        </p>
      </section>
    )
  }

  /*
   * 오류·호출 제한 배너가 이미 실제 원인을 설명하므로, 아직 성공한 결과가 없는 이 상태를
   * "빈 영역 + 조건 초기화"로 보여주면 안 된다(잘못된 원인을 안내하게 된다).
   */
  if (isBlocked) {
    return (
      <section className={styles.list} aria-label="현재 영역 맛집 목록">
        <p className={styles.state}>결과를 표시할 수 없습니다.</p>
      </section>
    )
  }

  if (!view || view.kind === 'empty') {
    return (
      <section className={styles.list} aria-label="현재 영역 맛집 목록">
        <div className={styles.state}>
          <p>이 영역에 조건과 일치하는 맛집이 없습니다.</p>
          <button type="button" className={styles.resetButton} onClick={onResetFilters}>
            조건 초기화
          </button>
        </div>
      </section>
    )
  }

  if (view.kind === 'tooMany') {
    return (
      <section className={styles.list} aria-label="현재 영역 맛집 목록">
        <p className={styles.state} role="alert">
          결과가 너무 많습니다. 지도를 확대해 주세요.
        </p>
      </section>
    )
  }

  return (
    <section className={styles.list} aria-label="현재 영역 맛집 목록">
      <ul className={styles.items}>
        {view.items.map((item) => (
          <li key={item.id}>
            <button
              type="button"
              className={styles.item}
              aria-pressed={isMapPointSelected(item.id, selectedId)}
              onClick={() => onSelect(item.id)}
            >
              <span className={styles.itemName}>{item.name}</span>
              <span className={styles.itemMeta}>
                {item.category} · {item.addressSummary}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
