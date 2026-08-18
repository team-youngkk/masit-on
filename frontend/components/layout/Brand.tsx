import { cn } from '@/lib/cn'

import styles from './Brand.module.css'

/*
 * 브랜드 마크와 서비스명. 헤더와 푸터가 함께 쓴다.
 * 서비스명 표기는 `맛잇온`이다. 와이어프레임 이미지의 `맛있온`은 오기다.
 * (docs/04-product/wireframes/README.md 4절)
 */
export function Brand({ className }: { className?: string }) {
  return (
    <span className={cn(styles.brand, className)}>
      <span className={styles.mark} aria-hidden="true">
        M
      </span>
      맛잇온
    </span>
  )
}
