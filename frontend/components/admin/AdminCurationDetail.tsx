'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { AdminCuration, curationMessageFor, getAdminCuration, replaceCurationRestaurants, setCurationPublication, updateAdminCuration } from '@/lib/admin/curations'
import { moveItem, parseRestaurantIds, validateCurationText } from '@/lib/admin/curations-coordination'

import styles from './AdminCurationScreen.module.css'

export function AdminCurationDetail({ curationId }: { curationId: string }) {
  const [data, setData] = useState<AdminCuration | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [restaurantIds, setRestaurantIds] = useState<string[]>([])
  const [newRestaurantId, setNewRestaurantId] = useState('')
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState(false)
  const [notice, setNotice] = useState('')

  const apply = useCallback((next: AdminCuration) => {
    setData(next); setTitle(next.title); setDescription(next.description)
    setRestaurantIds([...next.items].sort((a, b) => a.position - b.position).map((item) => item.restaurantId))
  }, [])

  async function retryLoad() {
    setBusy(true); setError(false); setNotice('큐레이션 상세를 다시 불러오고 있습니다.')
    try { apply(await getAdminCuration(curationId)); setNotice('') }
    catch (reason) { setError(true); setNotice(curationMessageFor(reason)) }
    finally { setBusy(false) }
  }

  useEffect(() => {
    let active = true
    setBusy(true)
    void getAdminCuration(curationId).then((next) => { if (active) { apply(next); setError(false); setNotice('') } })
      .catch((reason) => { if (active) { setError(true); setNotice(curationMessageFor(reason)) } })
      .finally(() => { if (active) setBusy(false) })
    return () => { active = false }
  }, [apply, curationId])

  async function run(action: () => Promise<AdminCuration>, success: string) {
    setBusy(true); setError(false); setNotice('저장하고 있습니다.')
    try { apply(await action()); setNotice(success) }
    catch (reason) { setError(true); setNotice(curationMessageFor(reason)) }
    finally { setBusy(false) }
  }

  function addRestaurant() {
    const parsed = parseRestaurantIds([...restaurantIds, newRestaurantId].join('\n'))
    if (parsed.errors.length) { setError(true); setNotice(parsed.errors.join(' ')); return }
    setRestaurantIds(parsed.ids); setNewRestaurantId(''); setError(false)
    setNotice('목록에 추가했습니다. 서버 반영은 구성 순서 저장을 눌러야 완료됩니다.')
  }

  if (busy && !data) return <p role="status">큐레이션 상세를 불러오는 중입니다.</p>
  if (!data) return <div className={styles.stack}><p className={styles.error} role="alert">{notice || '큐레이션을 불러오지 못했습니다.'}</p><Button variant="secondary" disabled={busy} onClick={() => void retryLoad()}>다시 시도</Button></div>
  const warnings = data.items.filter((item) => item.warning || (item.availability && item.availability !== 'PUBLIC'))

  return <div className={styles.screen}>
    <div className={styles.toolbar}><Link href="/admin/curations">← 목록으로</Link><span className={styles.badge}>{data.status === 'PUBLISHED' ? '게시 중' : '초안'}</span></div>
    {warnings.length ? <div className={styles.warning} role="alert"><strong>공개 노출 경고</strong>{warnings.map((item) => <p key={item.restaurantId}>{item.restaurantId}: {item.warning ?? `${item.availability} 상태로 공개 화면에서 숨겨집니다.`}</p>)}</div> : null}

    <form className={`${styles.form} ${styles.panel}`} onSubmit={(event) => { event.preventDefault(); const errors = validateCurationText(title, description); if (errors.length) { setError(true); setNotice(errors.join(' ')); return } void run(() => updateAdminCuration(curationId, title, description), '제목과 설명을 저장했습니다.') }}>
      <h2>기본 정보</h2>
      <label>제목 <span>1~100자</span><input maxLength={100} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
      <label>설명 <span>0~1000자</span><textarea rows={5} maxLength={1000} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
      <Button type="submit" disabled={busy}>기본 정보 저장</Button>
    </form>

    <section className={styles.panel} aria-labelledby="restaurants-heading">
      <h2 id="restaurants-heading">맛집 구성과 순서 ({restaurantIds.length}/20)</h2>
      <p className={styles.hint}>공개·활성 맛집 식별자만 저장할 수 있습니다. 저장하면 현재 서버 구성을 아래 목록 전체로 교체합니다.</p>
      <div className={styles.actions}>
        <label>추가할 맛집 식별자<input value={newRestaurantId} onChange={(event) => setNewRestaurantId(event.target.value)} /></label>
        <Button variant="secondary" disabled={busy || !newRestaurantId.trim()} onClick={addRestaurant}>목록에 추가</Button>
      </div>
      {restaurantIds.length ? <ol className={styles.orderList}>{restaurantIds.map((id, index) => <li className={styles.restaurantRow} key={id}>
        <strong>{index + 1}</strong><code>{id}</code><span className={styles.rowActions}>
          <Button variant="secondary" disabled={busy || index === 0} onClick={() => setRestaurantIds(moveItem(restaurantIds, index, -1))}>위로</Button>
          <Button variant="secondary" disabled={busy || index === restaurantIds.length - 1} onClick={() => setRestaurantIds(moveItem(restaurantIds, index, 1))}>아래로</Button>
          <Button variant="secondary" disabled={busy} onClick={() => setRestaurantIds(restaurantIds.filter((_, target) => target !== index))}>제거</Button>
        </span>
      </li>)}</ol> : <p className={styles.hint}>구성된 맛집이 없습니다. 빈 목록도 저장할 수 있습니다.</p>}
      <Button disabled={busy} onClick={() => { const parsed = parseRestaurantIds(restaurantIds.join('\n')); if (parsed.errors.length) { setError(true); setNotice(parsed.errors.join(' ')); return } void run(() => replaceCurationRestaurants(curationId, parsed.ids), '맛집 구성과 순서를 전체 교체했습니다.') }}>구성 순서 저장</Button>
    </section>

    <section className={`${styles.panel} ${styles.dangerZone}`} aria-labelledby="publication-heading">
      <h2 id="publication-heading">게시 상태</h2>
      <p>{data.status === 'PUBLISHED' ? '현재 공개 메인 목록에 노출됩니다.' : '현재 공개 화면에 노출되지 않습니다.'}</p>
      <Button variant={data.status === 'PUBLISHED' ? 'secondary' : 'primary'} disabled={busy} onClick={() => void run(() => setCurationPublication(curationId, data.status === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED'), data.status === 'PUBLISHED' ? '게시를 중단했습니다.' : '큐레이션을 게시했습니다. 메인 순서는 목록 화면에서 조정할 수 있습니다.')}>
        {data.status === 'PUBLISHED' ? '게시 중단' : '게시'}
      </Button>
    </section>
    {notice ? <p className={error ? styles.error : styles.notice} role={error ? 'alert' : 'status'}>{notice}</p> : null}
  </div>
}
