'use client'

import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import {
  getFavoriteState,
  setFavoriteState,
} from '@/lib/member/personal-restaurants'

import styles from './FavoriteButton.module.css'

type FavoriteButtonProps = {
  restaurantId: string
  restaurantName: string
  returnTo: string
  compact?: boolean
}

type LoadState = 'loading' | 'ready' | 'anonymous' | 'error'

function isAuthenticationError(reason: unknown): boolean {
  return reason instanceof Response && reason.status === 401
}

export function FavoriteButton({
  restaurantId,
  restaurantName,
  returnTo,
  compact = false,
}: FavoriteButtonProps) {
  const { status } = useMemberSession()
  const requestSequence = useRef(0)
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [favorited, setFavorited] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [mutationError, setMutationError] = useState(false)

  const loadFavorite = useCallback(async () => {
    const sequence = ++requestSequence.current
    if (status !== 'authenticated') {
      setFavorited(false)
      setMutationError(false)
      setSubmitting(false)
      setLoadState(status === 'loading' ? 'loading' : 'anonymous')
      return
    }

    setLoadState('loading')
    setMutationError(false)

    try {
      const nextFavorited = await getFavoriteState(restaurantId)
      if (sequence !== requestSequence.current) return
      setFavorited(nextFavorited)
      setLoadState('ready')
    } catch (reason) {
      if (sequence !== requestSequence.current) return
      setLoadState(isAuthenticationError(reason) ? 'anonymous' : 'error')
    }
  }, [restaurantId, status])

  useEffect(() => {
    void loadFavorite()
    return () => {
      requestSequence.current += 1
    }
  }, [loadFavorite])

  async function toggleFavorite() {
    const sequence = ++requestSequence.current
    const confirmedState = favorited
    const requestedState = !confirmedState

    setFavorited(requestedState)
    setSubmitting(true)
    setMutationError(false)

    try {
      const savedState = await setFavoriteState(restaurantId, requestedState)
      if (sequence !== requestSequence.current) return
      setFavorited(savedState)
    } catch (reason) {
      if (sequence !== requestSequence.current) return
      setFavorited(confirmedState)
      if (isAuthenticationError(reason)) {
        setLoadState('anonymous')
      } else {
        setMutationError(true)
      }
    } finally {
      if (sequence === requestSequence.current) {
        setSubmitting(false)
      }
    }
  }

  if (loadState === 'loading') {
    return (
      <Button
        variant="secondary"
        className={compact ? `${styles.control} ${styles.compactControl}` : styles.control}
        disabled
        aria-label={`${restaurantName} 찜 상태 확인 중`}
      >
        {compact ? (
          <>
            <span aria-hidden="true">♡</span>
            <span className={styles.srOnly}>찜 상태 확인 중…</span>
          </>
        ) : (
          '찜 확인 중…'
        )}
      </Button>
    )
  }

  if (loadState === 'anonymous') {
    const loginHref = `/login?returnTo=${encodeURIComponent(returnTo)}`

    return (
      <div className={styles.guidance}>
        <Link
          href={loginHref}
          className={
            compact
              ? `${styles.loginLink} ${styles.compactLoginLink}`
              : styles.loginLink
          }
          aria-label={`${restaurantName} 찜을 위해 로그인`}
        >
          {compact ? (
            <>
              <span aria-hidden="true">♡</span>
              <span className={styles.srOnly}>찜을 위해 로그인</span>
            </>
          ) : (
            '로그인 후 찜하기'
          )}
        </Link>
      </div>
    )
  }

  if (loadState === 'error') {
    return (
      <div className={styles.feedback}>
        <Button
          variant="secondary"
          className={
            compact ? `${styles.control} ${styles.compactControl}` : styles.control
          }
          onClick={() => void loadFavorite()}
          aria-label={`${restaurantName} 찜 상태 다시 확인`}
        >
          {compact ? (
            <>
              <span aria-hidden="true">↻</span>
              <span className={styles.srOnly}>찜 상태 다시 확인</span>
            </>
          ) : (
            '찜 상태 다시 확인'
          )}
        </Button>
        <span className={styles.error} role="alert">
          찜 상태를 불러오지 못했습니다.
        </span>
      </div>
    )
  }

  return (
    <div className={styles.feedback}>
      <Button
        variant="secondary"
        className={
          compact ? `${styles.control} ${styles.compactControl}` : styles.control
        }
        aria-pressed={favorited}
        aria-label={`${restaurantName} ${favorited ? '찜 해제' : '찜하기'}`}
        disabled={submitting}
        onClick={() => void toggleFavorite()}
      >
        <span aria-hidden="true">{favorited ? '♥' : '♡'}</span>
        {compact ? (
          <span className={styles.srOnly}>
            {submitting ? '저장 중…' : favorited ? '찜 해제' : '찜하기'}
          </span>
        ) : (
          (submitting ? '저장 중…' : favorited ? '찜 해제' : '찜하기')
        )}
      </Button>
      {mutationError ? (
        <span className={styles.error} role="alert">
          저장하지 못했습니다. 다시 시도해 주세요.
        </span>
      ) : null}
    </div>
  )
}
