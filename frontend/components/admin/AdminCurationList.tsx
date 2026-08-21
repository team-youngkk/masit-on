'use client'

import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { moveItem, nextCurationPage } from '@/lib/admin/curations-coordination'
import { AdminCuration, CurationStatus, curationMessageFor, getAdminCurations, replaceMainCurationOrder } from '@/lib/admin/curations'

import styles from './AdminCurationScreen.module.css'

const LABELS: Record<CurationStatus, string> = { DRAFT: '초안', PUBLISHED: '게시 중' }

export function AdminCurationList() {
  const [status, setStatus] = useState<CurationStatus | ''>('')
  const [page, setPage] = useState(1)
  const [items, setItems] = useState<AdminCuration[]>([])
  const [published, setPublished] = useState<AdminCuration[]>([])
  const [pageInfo, setPageInfo] = useState({ totalPages: 0, hasNext: false, totalElements: 0 })
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState(false)
  const [notice, setNotice] = useState('')
  const requestId = useRef(0)

  const load = useCallback(async () => {
    const currentRequest = ++requestId.current
    const [list, publishedList] = await Promise.all([
      getAdminCurations(page, status),
      getAdminCurations(1, 'PUBLISHED'),
    ])
    if (currentRequest !== requestId.current) return
    setItems(list.items)
    setPage(list.page.number)
    setPageInfo(list.page)
    setPublished([...publishedList.items].sort((a, b) => (a.mainPosition ?? 999) - (b.mainPosition ?? 999)))
  }, [page, status])

  useEffect(() => {
    let active = true
    setBusy(true)
    void load().then(() => {
      if (!active) return
      setError(false); setNotice('')
    }).catch((reason) => {
      if (!active) return
      setError(true); setNotice(curationMessageFor(reason))
    }).finally(() => { if (active) setBusy(false) })
    return () => { active = false }
  }, [load])

  function changeStatus(nextStatus: CurationStatus | '') {
    const next = nextCurationPage({ status, page }, { status: nextStatus })
    setStatus(next.status); setPage(next.page)
  }

  async function saveMainOrder() {
    setBusy(true); setError(false); setNotice('메인 노출 순서를 저장하고 있습니다.')
    try {
      await replaceMainCurationOrder(published.map((item) => item.curationId))
      await load()
      setNotice('게시 큐레이션의 메인 노출 순서를 저장했습니다.')
    } catch (reason) {
      setError(true); setNotice(curationMessageFor(reason))
    } finally { setBusy(false) }
  }

  return <div className={styles.screen}>
    <div className={styles.toolbar}>
      <label>상태 필터<select value={status} disabled={busy} onChange={(event) => changeStatus(event.target.value as CurationStatus | '')}>
        <option value="">전체</option><option value="DRAFT">초안</option><option value="PUBLISHED">게시 중</option>
      </select></label>
      <Link className={styles.linkButton} href="/admin/curations/new">새 큐레이션 만들기</Link>
    </div>

    <section className={styles.panel} aria-labelledby="main-order-heading">
      <h2 id="main-order-heading">메인 노출 순서</h2>
      <p className={styles.hint}>게시 중인 큐레이션 전체를 위에서부터 노출합니다. 최대 5개입니다.</p>
      {published.length ? <ol className={styles.orderList}>
        {published.map((item, index) => <li className={styles.restaurantRow} key={item.curationId}>
          <strong>{index + 1}</strong><span>{item.title}</span>
          <span className={styles.rowActions}>
            <Button variant="secondary" disabled={busy || index === 0} onClick={() => setPublished(moveItem(published, index, -1))}>위로</Button>
            <Button variant="secondary" disabled={busy || index === published.length - 1} onClick={() => setPublished(moveItem(published, index, 1))}>아래로</Button>
          </span>
        </li>)}
      </ol> : <p className={styles.hint}>현재 게시 중인 큐레이션이 없습니다.</p>}
      <div className={styles.formActions}>
        <Button disabled={busy} onClick={() => void saveMainOrder()}>메인 순서 저장</Button>
      </div>
    </section>

    {busy && !items.length ? <p role="status">큐레이션 목록을 불러오는 중입니다.</p> : null}
    {!busy && !items.length && !error ? <p className={styles.notice}>조건에 맞는 큐레이션이 없습니다.</p> : null}
    <ul className={styles.list}>
      {items.map((item) => <li className={styles.card} key={item.curationId}>
        <span className={`${styles.badge} ${item.status === 'PUBLISHED' ? styles.activeBadge : styles.draftBadge}`}>{LABELS[item.status]}</span>
        <h2><Link href={`/admin/curations/${encodeURIComponent(item.curationId)}`}>{item.title}</Link></h2>
        <p>{item.description || '설명 없음'}</p>
        <p className={styles.meta}>맛집 {item.restaurantCount ?? 0}곳 · 최근 수정 {new Date(item.updatedAt).toLocaleString('ko-KR')}</p>
        {item.hasHiddenRestaurants
          ? <p className={styles.warning}>비공개 또는 비활성 맛집이 포함되어 공개 화면에서 숨겨질 수 있습니다.</p> : null}
      </li>)}
    </ul>
    <nav className={styles.pagination} aria-label="큐레이션 목록 페이지">
      <Button variant="secondary" disabled={busy || page <= 1} onClick={() => setPage(page - 1)}>이전</Button>
      <span>{page} / {Math.max(pageInfo.totalPages, 1)} 페이지 · 총 {pageInfo.totalElements}건</span>
      <Button variant="secondary" disabled={busy || !pageInfo.hasNext} onClick={() => setPage(page + 1)}>다음</Button>
    </nav>
    {notice ? <p className={error ? styles.error : styles.notice} role={error ? 'alert' : 'status'}>{notice}</p> : null}
  </div>
}
