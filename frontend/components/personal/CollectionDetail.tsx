'use client'

import Link from 'next/link'
import { FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { CollectionScreenState } from './CollectionScreenState'
import {
  collectionNameError,
  previousCollectionPageAfterRemoval,
} from '@/lib/member/collections-coordination'
import {
  CollectionApiError,
  type CollectionDetail as CollectionDetailData,
  deleteCollection,
  getCollection,
  removeRestaurantFromCollection,
  renameCollection,
} from '@/lib/member/collections'

import styles from './collection-pages.module.css'

type ErrorState = { status?: number; message: string; traceId?: string }

function errorState(error: unknown, fallback: string): ErrorState {
  return error instanceof CollectionApiError
    ? { status: error.status, message: error.message, traceId: error.traceId }
    : { message: fallback }
}

function pageNumbers(current: number, total: number): number[] {
  if (total < 1) return []
  const start = Math.max(1, Math.min(current - 2, total - 4))
  const end = Math.min(total, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
}

export function CollectionDetail({ collectionId, page, size }: {
  collectionId: string
  page: number
  size: number
}) {
  const router = useRouter()
  const { status } = useMemberSession()
  const sequence = useRef(0)
  const [data, setData] = useState<CollectionDetailData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ErrorState | null>(null)
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<ErrorState | null>(null)
  const [action, setAction] = useState<'rename' | 'delete' | string | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const load = useCallback(async () => {
    const request = ++sequence.current
    setLoading(true)
    setError(null)
    try {
      const nextData = await getCollection(collectionId, page, size)
      if (request !== sequence.current) return null
      setData(nextData)
      setName(nextData.name)
      return nextData
    } catch (caught) {
      if (request === sequence.current) {
        setError(errorState(caught, '컬렉션을 불러오지 못했습니다.'))
      }
      return null
    } finally {
      if (request === sequence.current) setLoading(false)
    }
  }, [collectionId, page, size])

  useEffect(() => {
    if (status === 'authenticated') {
      void load()
      return () => { sequence.current += 1 }
    }
    sequence.current += 1
    setData(null)
    setError(null)
    setLoading(status === 'loading')
  }, [load, status])

  useEffect(() => {
    if (data && data.items.length === 0 && data.restaurantCount > 0 && page > data.page.totalPages) {
      router.replace(`/me/collections/${encodeURIComponent(collectionId)}?page=${data.page.totalPages}&size=${size}`)
    }
  }, [collectionId, data, page, router, size])

  function goToPage(nextPage: number) {
    if (action) return
    router.push(`/me/collections/${encodeURIComponent(collectionId)}?page=${nextPage}&size=${size}`)
  }

  async function submitRename(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const validationError = collectionNameError(name)
    setNameError(validationError)
    setActionError(null)
    if (validationError || action) return
    setAction('rename')
    try {
      const renamed = await renameCollection(collectionId, name.trim())
      setData((current) => current ? { ...current, ...renamed } : current)
      setName(renamed.name)
    } catch (caught) {
      setActionError(errorState(caught, '이름을 변경하지 못했습니다.'))
    } finally {
      setAction(null)
    }
  }

  async function confirmDelete() {
    if (action) return
    setAction('delete')
    setActionError(null)
    try {
      await deleteCollection(collectionId)
      router.replace('/me/collections')
    } catch (caught) {
      setActionError(errorState(caught, '컬렉션을 삭제하지 못했습니다.'))
      setAction(null)
    }
  }

  async function removeRestaurant(restaurantId: string) {
    if (action) return
    setAction(restaurantId)
    setActionError(null)
    try {
      await removeRestaurantFromCollection(collectionId, restaurantId)
      const refreshed = await load()
      const previousPage = previousCollectionPageAfterRemoval(
        page,
        refreshed?.items.length ?? -1,
      )
      if (previousPage !== null) {
        router.push(`/me/collections/${encodeURIComponent(collectionId)}?page=${previousPage}&size=${size}`)
      }
    } catch (caught) {
      setActionError(errorState(caught, '맛집을 컬렉션에서 제거하지 못했습니다.'))
    } finally {
      setAction(null)
    }
  }

  const currentPath = `/me/collections/${encodeURIComponent(collectionId)}?page=${page}&size=${size}`
  const loginHref = `/login?returnTo=${encodeURIComponent(currentPath)}`
  const unauthenticated = status === 'anonymous' || error?.status === 401 || actionError?.status === 401

  return (
    <section className={styles.page}>
      <Link className={styles.back} href="/me/collections">← 내 컬렉션</Link>
      {status === 'loading' || (loading && !data) ? (
        <CollectionScreenState state="loading" className={styles.state} message="컬렉션을 불러오는 중입니다." />
      ) : unauthenticated ? (
        <CollectionScreenState
          state="authentication"
          className={styles.state}
          message="로그인이 필요합니다."
          action={<Link className={styles.cta} href={loginHref}>로그인하기</Link>}
        />
      ) : error ? (
        <CollectionScreenState
          state={error.status === 404 ? 'not-found' : 'error'}
          className={styles.error}
          traceClassName={styles.traceId}
          message={error.status === 404 ? '컬렉션을 찾을 수 없습니다.' : error.message}
          traceId={error.traceId}
          action={error.status === 404 ? undefined : <Button variant="secondary" onClick={() => void load()}>다시 시도</Button>}
        />
      ) : data ? (
        <>
          <header className={styles.header}>
            <div>
              <h1>{data.name}</h1>
              <p>현재 볼 수 있는 맛집 {data.restaurantCount}곳</p>
            </div>
          </header>

          <div className={styles.manage}>
            <form className={styles.form} onSubmit={submitRename}>
              <label htmlFor="collection-rename">컬렉션 이름</label>
              <div className={styles.formRow}>
                <input
                  id="collection-rename"
                  value={name}
                  maxLength={100}
                  disabled={action !== null}
                  aria-invalid={nameError ? true : undefined}
                  aria-describedby={nameError ? 'collection-rename-error' : undefined}
                  onChange={(event) => { setName(event.target.value); setNameError(null); setActionError(null) }}
                />
                <Button type="submit" disabled={action !== null}>{action === 'rename' ? '저장 중…' : '이름 저장'}</Button>
              </div>
              {nameError ? <p id="collection-rename-error" className={styles.fieldError}>{nameError}</p> : null}
            </form>

            {!confirmingDelete ? (
              <Button variant="secondary" disabled={action !== null} onClick={() => setConfirmingDelete(true)}>컬렉션 삭제</Button>
            ) : (
              <div className={styles.confirm} role="group" aria-labelledby="delete-collection-title">
                <h2 id="delete-collection-title">컬렉션을 삭제할까요?</h2>
                <p>컬렉션의 맛집 구성만 삭제되며 맛집 원본에는 영향을 주지 않습니다.</p>
                <div className={styles.actions}>
                  <Button variant="secondary" disabled={action !== null} onClick={() => setConfirmingDelete(false)}>취소</Button>
                  <Button disabled={action !== null} onClick={() => void confirmDelete()}>{action === 'delete' ? '삭제 중…' : '삭제 확인'}</Button>
                </div>
              </div>
            )}
          </div>

          {actionError && actionError.status !== 401 ? (
            <div className={styles.error} role="alert">
              <p>{actionError.message}</p>
              {actionError.traceId ? <p className={styles.traceId}>traceId: {actionError.traceId}</p> : null}
            </div>
          ) : null}

          {data.restaurantCount === 0 ? (
            <CollectionScreenState
              state="empty"
              className={styles.state}
              message="이 컬렉션에서 볼 수 있는 맛집이 없습니다."
              action={<Link className={styles.cta} href="/restaurants">맛집 탐색하기</Link>}
            />
          ) : data.items.length > 0 ? (
            <ul className={styles.list} aria-busy={loading}>
              {data.items.map((item) => (
                <li key={item.restaurantId}>
                  <Card
                    level={2}
                    title={<Link href={`/restaurants/${encodeURIComponent(item.restaurantId)}`}>{item.name}</Link>}
                    meta={item.roadAddress}
                  >
                    <div className={styles.actions}>
                      <Button variant="secondary" disabled={action !== null} onClick={() => void removeRestaurant(item.restaurantId)}>
                        {action === item.restaurantId ? '제거 중…' : '컬렉션에서 제거'}
                      </Button>
                    </div>
                  </Card>
                </li>
              ))}
            </ul>
          ) : (
            <p className={styles.state} aria-live="polite">마지막 페이지로 이동하는 중입니다.</p>
          )}

          {data.page.totalPages > 1 ? (
            <nav className={styles.pagination} aria-label="컬렉션 맛집 페이지 이동">
              <p>{data.page.number} / {data.page.totalPages} 페이지 (총 {data.page.totalElements}곳)</p>
              <div className={styles.actions}>
                <Button variant="secondary" disabled={data.page.number <= 1 || action !== null} onClick={() => goToPage(data.page.number - 1)}>이전</Button>
                {pageNumbers(data.page.number, data.page.totalPages).map((number) => (
                  <button
                    type="button"
                    key={number}
                    className={number === data.page.number ? styles.currentPage : styles.pageButton}
                    aria-current={number === data.page.number ? 'page' : undefined}
                    disabled={action !== null}
                    onClick={() => goToPage(number)}
                  >{number}</button>
                ))}
                <Button variant="secondary" disabled={!data.page.hasNext || action !== null} onClick={() => goToPage(data.page.number + 1)}>다음</Button>
              </div>
            </nav>
          ) : null}
        </>
      ) : null}
    </section>
  )
}
