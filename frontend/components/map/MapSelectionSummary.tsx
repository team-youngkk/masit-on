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
          <p className={styles.text}>
            선택: {selected.name} · {selected.category} · {selected.addressSummary}
          </p>
          <Link
            href={`/restaurants/${encodeURIComponent(selected.id)}`}
            className={styles.detailLink}
          >
            상세 보기
          </Link>
        </>
      ) : (
        <p className={styles.text}>맛집을 선택하면 여기에 요약이 표시됩니다.</p>
      )}
    </section>
  )
}
