import Link from 'next/link'

import type { MapPointItem } from '@/lib/map/map-points-response'

import styles from './MapSelectionSummary.module.css'

type MapSelectionSummaryProps = {
  selected: MapPointItem | null
}

export function MapSelectionSummary({ selected }: MapSelectionSummaryProps) {
  return (
    <section className={styles.summary} aria-live="polite">
      {selected ? (
        <>
          <div className={styles.content}>
            <p className={styles.eyebrow}>선택한 맛집</p>
            <p className={styles.name}>{selected.name}</p>
            <p className={styles.text}>{selected.category} · {selected.addressSummary}</p>
          </div>
          <Link
            href={`/restaurants/${encodeURIComponent(selected.id)}`}
            className={styles.detailLink}
          >
            상세 보기
          </Link>
        </>
      ) : (
        <p className={styles.empty}>지도 마커 또는 목록을 선택하면 상세 정보가 표시됩니다.</p>
      )}
    </section>
  )
}
