import type { ReactNode } from 'react'

import styles from './Card.module.css'

type CardProps = {
  title: ReactNode
  /* 제목 heading 레벨. 상위 heading에 맞춰 지정해 문서 구조가 건너뛰지 않게 한다. */
  level?: 2 | 3 | 4
  /* 카드 부제. 맛집 카드에서는 자치구·카테고리를 넣는다. */
  meta?: ReactNode
  children?: ReactNode
}

/*
 * 표시 전용 카드라 Server Component로 둔다.
 * 평점·리뷰·대표 이미지는 확정 데이터 모델에 없으므로 슬롯을 만들지 않는다.
 */
export function Card({ title, level = 3, meta, children }: CardProps) {
  const Heading = `h${level}` as 'h2' | 'h3' | 'h4'

  return (
    <article className={styles.card}>
      <Heading className={styles.title}>{title}</Heading>
      {/* meta={0} 같은 falsy 값도 표시해야 하므로 null·undefined만 걸러낸다 */}
      {meta != null ? <p className={styles.meta}>{meta}</p> : null}
      {children}
    </article>
  )
}
