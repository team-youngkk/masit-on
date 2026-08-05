'use client'

import { useRouter } from 'next/navigation'
import { useTransition } from 'react'

import styles from './popular.module.css'

export function RetryButton() {
  const router = useRouter()
  const [pending, startTransition] = useTransition()

  return (
    <button
      type="button"
      className={styles.retryButton}
      disabled={pending}
      aria-live="polite"
      onClick={() => startTransition(() => router.refresh())}
    >
      {pending ? '다시 시도 중' : '다시 시도'}
    </button>
  )
}
