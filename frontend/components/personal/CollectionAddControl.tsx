'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import { collectionAddErrorMessage } from '@/lib/member/collections-coordination'
import {
  CollectionApiError,
  addRestaurantToCollection,
  getCollections,
  type CollectionSummary,
} from '@/lib/member/collections'

import styles from './CollectionAddControl.module.css'

export function CollectionAddControl({ restaurantId, returnTo }: {
  restaurantId: string
  returnTo: string
}) {
  const { status } = useMemberSession()
  const [collections, setCollections] = useState<CollectionSummary[]>([])
  const [selectedId, setSelectedId] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<{ status?: number; message: string; traceId?: string } | null>(null)
  const [retrySequence, setRetrySequence] = useState(0)

  useEffect(() => {
    let active = true
    if (status !== 'authenticated') {
      setCollections([])
      setSelectedId('')
      setLoading(status === 'loading')
      return () => { active = false }
    }
    setLoading(true)
    setError(null)
    getCollections()
      .then((items) => {
        if (!active) return
        setCollections(items)
        setSelectedId((current) => current && items.some((item) => item.collectionId === current)
          ? current
          : (items[0]?.collectionId ?? ''))
      })
      .catch((reason) => {
        if (!active) return
        const apiError = reason instanceof CollectionApiError ? reason : null
        setError({
          status: apiError?.status,
          message: apiError?.message ?? '컬렉션 목록을 불러오지 못했습니다.',
          traceId: apiError?.traceId,
        })
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [retrySequence, status])

  async function add() {
    if (!selectedId || submitting) return
    setSubmitting(true)
    setMessage(null)
    setError(null)
    try {
      await addRestaurantToCollection(selectedId, restaurantId)
      const selected = collections.find((item) => item.collectionId === selectedId)
      setMessage(`‘${selected?.name ?? '선택한 컬렉션'}’에 담았습니다.`)
    } catch (reason) {
      const apiError = reason instanceof CollectionApiError ? reason : null
      setError({
        status: apiError?.status,
        message: collectionAddErrorMessage(apiError?.code),
        traceId: apiError?.traceId,
      })
    } finally {
      setSubmitting(false)
    }
  }

  if (status === 'anonymous' || error?.status === 401) {
    return <Link href={`/login?returnTo=${encodeURIComponent(returnTo)}`}>로그인 후 컬렉션에 담기</Link>
  }

  return (
    <div className={styles.control}>
      <div className={styles.row}>
        <select
          aria-label="담을 컬렉션"
          value={selectedId}
          disabled={loading || submitting || collections.length === 0}
          onChange={(event) => { setSelectedId(event.target.value); setMessage(null); setError(null) }}
        >
          {collections.length === 0 ? <option value="">컬렉션 없음</option> : null}
          {collections.map((item) => <option key={item.collectionId} value={item.collectionId}>{item.name}</option>)}
        </select>
        <Button variant="secondary" disabled={!selectedId || loading || submitting} onClick={() => void add()}>
          {submitting ? '담는 중…' : '컬렉션에 담기'}
        </Button>
      </div>
      {!loading && collections.length === 0 && !error ? (
        <p className={styles.message}><Link href="/me/collections">컬렉션을 먼저 만들어 주세요.</Link></p>
      ) : null}
      {message ? <p className={styles.message} role="status">{message}</p> : null}
      {error ? (
        <div role="alert">
          <p className={styles.error}>
            {error.message}{error.traceId ? ` traceId: ${error.traceId}` : ''}
          </p>
          {collections.length === 0 ? (
            <Button variant="secondary" onClick={() => setRetrySequence((value) => value + 1)}>
              컬렉션 다시 불러오기
            </Button>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
