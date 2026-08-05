'use client'

import { useRouter } from 'next/navigation'
import { useTransition } from 'react'

import styles from './curations.module.css'

export function RetryButton() {
  const router = useRouter()
  const [pending, startTransition] = useTransition()

  return (
    <button
      type="button"
      className={`${styles.actionLink} ${styles.retryButton}`}
      disabled={pending}
      onClick={() => startTransition(() => router.refresh())}
    >
      {pending ? '다시 시도 중' : '다시 시도'}
    </button>
  )
}
