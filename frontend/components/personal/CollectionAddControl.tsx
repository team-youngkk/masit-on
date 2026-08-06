'use client'

import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import {
  addThenRefreshCollectionOptions,
  collectionAddErrorMessage,
  collectionOptionSelection,
  isCollectionOptionDisabled,
} from '@/lib/member/collections-coordination'
import {
  CollectionApiError,
  addRestaurantToCollection,
  getCollectionOptions,
  type CollectionOption as CollectionOptionValue,
} from '@/lib/member/collections'

import { CollectionOption } from './CollectionOption'
import styles from './CollectionAddControl.module.css'

type ControlError = {
  status?: number
  message: string
  traceId?: string
}

function controlError(reason: unknown, fallback: string): ControlError {
  const apiError = reason instanceof CollectionApiError ? reason : null
  return {
    status: apiError?.status,
    message: apiError?.message ?? fallback,
    traceId: apiError?.traceId,
  }
}

export function CollectionAddControl({ restaurantId, returnTo }: {
  restaurantId: string
  returnTo: string
}) {
  const { status } = useMemberSession()
  const [options, setOptions] = useState<CollectionOptionValue[]>([])
  const [selectedId, setSelectedId] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<ControlError | null>(null)
  const [actionError, setActionError] = useState<ControlError | null>(null)
  const [retrySequence, setRetrySequence] = useState(0)
  const optionRequestId = useRef(0)
  const mutationRequestId = useRef(0)
  const mutationController = useRef<AbortController | null>(null)

  useEffect(() => () => {
    mutationRequestId.current += 1
    mutationController.current?.abort()
  }, [])

  useEffect(() => {
    const requestId = ++optionRequestId.current
    const controller = new AbortController()
    mutationRequestId.current += 1
    mutationController.current?.abort()

    if (status !== 'authenticated') {
      setOptions([])
      setSelectedId('')
      setLoading(status === 'loading')
      return () => controller.abort()
    }

    setLoading(true)
    setLoadError(null)
    setActionError(null)
    setMessage(null)
    setOptions([])
    setSelectedId('')
    getCollectionOptions(restaurantId, controller.signal)
      .then((items) => {
        if (controller.signal.aborted || optionRequestId.current !== requestId) return
        setOptions(items)
        setSelectedId((current) => collectionOptionSelection(items, current))
      })
      .catch((reason) => {
        if (controller.signal.aborted || optionRequestId.current !== requestId) return
        setLoadError(controlError(reason, '컬렉션 목록을 불러오지 못했습니다.'))
      })
      .finally(() => {
        if (!controller.signal.aborted && optionRequestId.current === requestId) setLoading(false)
      })

    return () => controller.abort()
  }, [restaurantId, retrySequence, status])

  async function add() {
    if (!selectedId || submitting) return
    const selected = options.find((item) => item.collectionId === selectedId)
    if (!selected || isCollectionOptionDisabled(selected.additionStatus)) return

    const requestId = ++mutationRequestId.current
    const controller = new AbortController()
    mutationController.current?.abort()
    mutationController.current = controller
    setSubmitting(true)
    setMessage(null)
    setLoadError(null)
    setActionError(null)

    try {
      const result = await addThenRefreshCollectionOptions(
        () => addRestaurantToCollection(selected.collectionId, restaurantId, controller.signal),
        () => getCollectionOptions(restaurantId, controller.signal),
      )
      if (controller.signal.aborted || mutationRequestId.current !== requestId) return

      optionRequestId.current += 1
      setOptions(result.options)
      setSelectedId((current) => collectionOptionSelection(result.options, current))
      if (result.additionError) {
        const apiError = result.additionError instanceof CollectionApiError
          ? result.additionError
          : null
        setActionError({
          status: apiError?.status,
          message: collectionAddErrorMessage(apiError?.code),
          traceId: apiError?.traceId,
        })
      } else {
        setMessage(`‘${selected.name}’에 담았습니다.`)
      }
    } catch (reason) {
      if (controller.signal.aborted || mutationRequestId.current !== requestId) return
      setOptions([])
      setSelectedId('')
      setLoadError(controlError(reason, '서버의 최신 컬렉션 상태를 불러오지 못했습니다.'))
    } finally {
      if (!controller.signal.aborted && mutationRequestId.current === requestId) {
        setSubmitting(false)
        mutationController.current = null
      }
    }
  }

  const error = loadError ?? actionError
  const selectedOption = options.find((option) => option.collectionId === selectedId)
  if (status === 'anonymous' || error?.status === 401) {
    return <Link href={`/login?returnTo=${encodeURIComponent(returnTo)}`}>로그인 후 컬렉션에 담기</Link>
  }

  return (
    <div className={styles.control}>
      <div className={styles.row}>
        <select
          aria-label="담을 컬렉션"
          value={selectedId}
          disabled={loading || submitting || options.length === 0}
          onChange={(event) => {
            setSelectedId(event.target.value)
            setMessage(null)
            setActionError(null)
          }}
        >
          {options.length === 0 ? <option value="">컬렉션 없음</option> : null}
          {options.map((option) => <CollectionOption key={option.collectionId} option={option} />)}
        </select>
        <Button
          variant="secondary"
          disabled={
            !selectedOption
            || isCollectionOptionDisabled(selectedOption.additionStatus)
            || loading
            || submitting
          }
          onClick={() => void add()}
        >
          {submitting ? '담는 중…' : '컬렉션에 담기'}
        </Button>
      </div>
      {!loading && options.length === 0 && !loadError ? (
        <p className={styles.message}><Link href="/me/collections">컬렉션을 먼저 만들어 주세요.</Link></p>
      ) : null}
      {message ? <p className={styles.message} role="status">{message}</p> : null}
      {error ? (
        <div role="alert">
          <p className={styles.error}>
            {error.message}{error.traceId ? ` traceId: ${error.traceId}` : ''}
          </p>
          {loadError ? (
            <Button
              variant="secondary"
              disabled={submitting}
              onClick={() => setRetrySequence((value) => value + 1)}
            >
              컬렉션 다시 불러오기
            </Button>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
