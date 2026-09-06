'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import { AdminApiError, messageFor } from '@/lib/admin/api'
import { adminTagScope, validateTagEdit, type TagEdit, type VisitTags } from '@/lib/admin/visit-tags-coordination'
import { getRestaurantVisitTags, saveVisitTags } from '@/lib/admin/visit-tags'
import { MEMBER_SESSION_CHANGED_EVENT } from '@/lib/member/auth'
import styles from './RestaurantVisitTagsPanel.module.css'

export function RestaurantVisitTagsPanel({ restaurantId }: { restaurantId: string }) {
  const { status, session } = useMemberSession()
  const accountId = adminTagScope(status, session)
  return accountId ? <AdminTags key={`${accountId}:${restaurantId}`} accountId={accountId} restaurantId={restaurantId} /> : null
}

function AdminTags({ accountId, restaurantId }: { accountId: string; restaurantId: string }) {
  const client = useQueryClient()
  const queryKey = ['auth', accountId, 'restaurant-visit-tags', restaurantId] as const
  const requests = useRef(new Set<AbortController>())
  const active = useRef(true)
  const saving = useRef(false)
  const [editing, setEditing] = useState<{ visitId: string; edit: TagEdit } | null>(null)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    active.current = true
    const discard = () => {
      active.current = false
      requests.current.forEach(controller => controller.abort())
      requests.current.clear()
      void client.cancelQueries({ queryKey: ['auth', accountId, 'restaurant-visit-tags', restaurantId] })
      client.removeQueries({ queryKey: ['auth', accountId, 'restaurant-visit-tags', restaurantId] })
    }
    window.addEventListener(MEMBER_SESSION_CHANGED_EVENT, discard)
    return () => { window.removeEventListener(MEMBER_SESSION_CHANGED_EVENT, discard); discard() }
  }, [accountId, restaurantId, client])

  async function tracked<T>(request: (signal: AbortSignal) => Promise<T>, signal?: AbortSignal): Promise<T> {
    const controller = new AbortController()
    requests.current.add(controller)
    try { return await request(signal ? AbortSignal.any([signal, controller.signal]) : controller.signal) }
    finally { requests.current.delete(controller) }
  }

  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => tracked(signal => getRestaurantVisitTags(restaurantId, accountId, signal), signal),
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
  const mutation = useMutation({
    mutationFn: ({ visitId, edit }: { visitId: string; edit: TagEdit }) =>
      tracked(signal => saveVisitTags(restaurantId, visitId, accountId, edit, signal)),
    retry: false,
    gcTime: 0,
  })

  function begin(visit: VisitTags) {
    setError('')
    setNotice('')
    setEditing({ visitId: visit.visitId, edit: { expectedVersion: visit.version, tagCodes: visit.tags.map(tag => tag.code), reason: '' } })
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!editing || saving.current || !active.current || !query.data) return
    const validation = validateTagEdit(editing.edit, query.data.tagOptions)
    if (validation) { setError(validation); return }
    saving.current = true
    setError('')
    try {
      await mutation.mutateAsync(editing)
      if (!active.current) return
      setEditing(null)
      setNotice('태그를 저장했습니다. 다음 자연어 검색부터 반영됩니다.')
      await query.refetch()
    } catch (reason) {
      if (!active.current) return
      if (reason instanceof AdminApiError && reason.status === 409) {
        setEditing(null)
        setNotice('다른 변경이 있어 저장하지 않았습니다. 최신 태그를 확인한 뒤 다시 편집해 주세요.')
        await query.refetch()
      } else {
        setError(messageFor(reason))
      }
    } finally { saving.current = false }
  }

  const options = query.data?.tagOptions ?? []
  return <section className={styles.panel} aria-label="관리자 방문 태그 관리">
    <h2>방문 태그 관리 <span className={styles.admin}>관리자</span></h2>
    <p>자연어 검색에 사용하는 태그입니다. 여러 태그 조건은 같은 방문에 함께 있어야 검색됩니다.</p>
    {notice && <p role="status">{notice}</p>}
    {error && <p role="alert" className={styles.error}>{error}</p>}
    {query.isPending ? <p role="status">방문 태그를 불러오는 중…</p> : query.isError ? <div>
      <p role="alert" className={styles.error}>{messageFor(query.error)}</p>
      <Button variant="secondary" disabled={query.isFetching} onClick={() => void query.refetch()}>다시 불러오기</Button>
    </div> : query.data.items.length === 0 ? <p>태그를 관리할 수 있는 공개 방문 콘텐츠가 없습니다.</p> :
      <ul className={styles.visits}>{query.data.items.map(visit => {
        const draft = editing?.visitId === visit.visitId ? editing.edit : null
        const inactive = visit.tags.filter(tag => !options.some(option => option.code === tag.code))
        const videoUrl = /^https?:\/\//i.test(visit.videoUrl) ? visit.videoUrl : null
        return <li key={visit.visitId} className={styles.visit}>
          <h3>{visit.creatorName}</h3>
          <p>{videoUrl ? <a href={videoUrl} target="_blank" rel="noopener noreferrer">{visit.videoTitle} ↗</a> : visit.videoTitle}</p>
          <div className={styles.tags}>{visit.tags.length ? visit.tags.map(tag => <span key={tag.code} className={styles.tag}>
            {tag.displayName}{inactive.some(item => item.code === tag.code) ? ' (비활성)' : ''}
          </span>) : <span>등록된 태그가 없습니다.</span>}</div>
          {draft ? <form onSubmit={submit} className={styles.form}>
            <fieldset disabled={mutation.isPending}>
              <legend>이 방문의 태그 선택 ({draft.tagCodes.length}/50)</legend>
              {inactive.length > 0 && <p>비활성 태그는 해제해야 저장할 수 있습니다.</p>}
              <div className={styles.options}>{[...options, ...inactive].map(tag => {
                const checked = draft.tagCodes.includes(tag.code)
                const disabled = !checked && (!options.some(option => option.code === tag.code) || draft.tagCodes.length >= 50)
                return <label key={tag.code}><input type="checkbox" checked={checked} disabled={disabled}
                  onChange={() => setEditing({ visitId: visit.visitId, edit: { ...draft, tagCodes: checked ? draft.tagCodes.filter(code => code !== tag.code) : [...draft.tagCodes, tag.code] } })} />
                  {tag.displayName}{inactive.some(item => item.code === tag.code) ? ' (비활성)' : ''}
                </label>
              })}</div>
              <label className={styles.reason}>수정 사유 (필수)
                <textarea required maxLength={1000} rows={3} value={draft.reason} onChange={event => setEditing({ visitId: visit.visitId, edit: { ...draft, reason: event.target.value } })} />
              </label>
              {draft.tagCodes.length === 0 && <p>저장하면 이 방문의 태그가 모두 해제됩니다.</p>}
              <div className={styles.actions}>
                <Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? '저장 중…' : '저장'}</Button>
                <Button variant="secondary" disabled={mutation.isPending} onClick={() => { setEditing(null); setError('') }}>취소</Button>
              </div>
            </fieldset>
          </form> : <Button variant="secondary" disabled={editing !== null || mutation.isPending || query.isFetching} onClick={() => begin(visit)}>태그 수정</Button>}
        </li>
      })}</ul>}
  </section>
}
