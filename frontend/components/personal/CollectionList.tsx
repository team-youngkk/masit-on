'use client'

import Link from 'next/link'
import { FormEvent, useCallback, useEffect, useRef, useState } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { CollectionScreenState } from './CollectionScreenState'
import {
  collectionNameError,
  creationAttemptFor,
  type CollectionCreationAttempt,
} from '@/lib/member/collections-coordination'
import {
  CollectionApiError,
  type CollectionSummary,
  createCollection,
  getCollections,
  newIdempotencyKey,
} from '@/lib/member/collections'

import styles from './collection-pages.module.css'

type ErrorState = { status?: number; message: string; traceId?: string }

function errorState(error: unknown, fallback: string): ErrorState {
  if (error instanceof CollectionApiError) {
    const message = error.code === 'COLLECTION_LIMIT_EXCEEDED'
      ? '컬렉션은 최대 20개까지 만들 수 있습니다.'
      : error.message
    return { status: error.status, message, traceId: error.traceId }
  }
  return { message: fallback }
}

function formatUpdatedAt(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

export function CollectionList() {
  const { status } = useMemberSession()
  const sequence = useRef(0)
  const creationAttempt = useRef<CollectionCreationAttempt | null>(null)
  const [items, setItems] = useState<CollectionSummary[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<ErrorState | null>(null)
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [createError, setCreateError] = useState<ErrorState | null>(null)
  const [creating, setCreating] = useState(false)

  const load = useCallback(async () => {
    const request = ++sequence.current
    setLoading(true)
    setLoadError(null)
    try {
      const nextItems = await getCollections()
      if (request === sequence.current) setItems(nextItems)
    } catch (error) {
      if (request === sequence.current) {
        setLoadError(errorState(error, '컬렉션 목록을 불러오지 못했습니다.'))
      }
    } finally {
      if (request === sequence.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (status === 'authenticated') {
      void load()
      return () => { sequence.current += 1 }
    }
    sequence.current += 1
    setItems(null)
    setLoadError(null)
    setLoading(status === 'loading')
  }, [load, status])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const validationError = collectionNameError(name)
    setNameError(validationError)
    setCreateError(null)
    if (validationError || creating || (items?.length ?? 0) >= 20) return

    const attempt = creationAttemptFor(creationAttempt.current, name, newIdempotencyKey)
    creationAttempt.current = attempt
    setCreating(true)
    try {
      await createCollection(attempt.normalizedName, attempt.idempotencyKey)
      creationAttempt.current = null
      setName('')
      await load()
    } catch (error) {
      setCreateError(errorState(error, '컬렉션을 만들지 못했습니다. 다시 시도해 주세요.'))
    } finally {
      setCreating(false)
    }
  }

  const loginHref = `/login?returnTo=${encodeURIComponent('/me/collections')}`
  const unauthenticated = status === 'anonymous' || loadError?.status === 401 || createError?.status === 401
  const atLimit = (items?.length ?? 0) >= 20

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>내 컬렉션</h1>
          <p>가고 싶은 맛집을 목적별로 모아 보세요.</p>
        </div>
        {items ? <span className={styles.count}>{items.length} / 20</span> : null}
      </header>

      {status === 'loading' || (loading && !items) ? (
        <CollectionScreenState state="loading" className={styles.state} message="컬렉션을 불러오는 중입니다." />
      ) : unauthenticated ? (
        <CollectionScreenState
          state="authentication"
          className={styles.state}
          message="로그인이 필요합니다."
          action={<Link className={styles.cta} href={loginHref}>로그인하기</Link>}
        />
      ) : loadError ? (
        <CollectionScreenState
          state="error"
          className={styles.error}
          traceClassName={styles.traceId}
          message={loadError.message}
          traceId={loadError.traceId}
          action={<Button variant="secondary" onClick={() => void load()}>다시 시도</Button>}
        />
      ) : (
        <>
          <form className={styles.form} onSubmit={submit}>
            <label htmlFor="collection-name">새 컬렉션 이름</label>
            <div className={styles.formRow}>
              <input
                id="collection-name"
                value={name}
                maxLength={100}
                disabled={creating || atLimit}
                aria-invalid={nameError ? true : undefined}
                aria-describedby={nameError ? 'collection-name-error' : undefined}
                onChange={(event) => {
                  setName(event.target.value)
                  setNameError(null)
                  setCreateError(null)
                }}
              />
              <Button type="submit" disabled={creating || atLimit}>
                {creating ? '만드는 중…' : '만들기'}
              </Button>
            </div>
            {nameError ? <p id="collection-name-error" className={styles.fieldError}>{nameError}</p> : null}
            {atLimit ? <p className={styles.limit}>컬렉션 20개를 모두 사용했습니다. 새로 만들려면 기존 컬렉션을 삭제해 주세요.</p> : null}
            {createError && createError.status !== 401 ? (
              <p className={styles.fieldError} role="alert">
                {createError.message}
                {createError.traceId ? <span className={styles.traceId}>traceId: {createError.traceId}</span> : null}
              </p>
            ) : null}
          </form>

          {items?.length === 0 ? (
            <CollectionScreenState
              state="empty"
              className={styles.state}
              message="아직 만든 컬렉션이 없습니다."
              action={<p>이름을 입력해 첫 컬렉션을 만들어 보세요.</p>}
            />
          ) : (
            <ul className={styles.list} aria-busy={loading}>
              {items?.map((item) => (
                <li key={item.collectionId}>
                  <Card
                    level={2}
                    title={<Link href={`/me/collections/${encodeURIComponent(item.collectionId)}`}>{item.name}</Link>}
                    meta={`맛집 ${item.restaurantCount}곳`}
                  >
                    <p className={styles.timestamp}>최근 수정: {formatUpdatedAt(item.updatedAt)}</p>
                  </Card>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  )
}
