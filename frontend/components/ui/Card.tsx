import type { ReactNode } from 'react'

import styles from './Card.module.css'

type CardProps = {
  title: ReactNode
  /* 카드 부제. 맛집 카드에서는 자치구·카테고리를 넣는다. */
  meta?: ReactNode
  children?: ReactNode
}

/*
 * 표시 전용 카드라 Server Component로 둔다.
 * 평점·리뷰·대표 이미지는 확정 데이터 모델에 없으므로 슬롯을 만들지 않는다.
 */
export function Card({ title, meta, children }: CardProps) {
  return (
    <article className={styles.card}>
      <h3 className={styles.title}>{title}</h3>
      {meta ? <p className={styles.meta}>{meta}</p> : null}
      {children}
    </article>
  )
}
