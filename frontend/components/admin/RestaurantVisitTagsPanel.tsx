'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import { AdminApiError, fieldErrorsFor, messageFor } from '@/lib/admin/api'
import {
  TAG_DEFINITION_TYPES,
  adminTagScope,
  appendTagDefinition,
  appendTagDefinitionToList,
  prepareTagDefinition,
  selectCreatedTag,
  validateTagDefinition,
  validateTagEdit,
  type RestaurantVisitTags,
  type TagDefinitionDraft,
  type TagDefinitionList,
  type TagEdit,
  type VisitTags,
} from '@/lib/admin/visit-tags-coordination'
import { createTagDefinition, getRestaurantVisitTags, getTagDefinitions, saveVisitTags } from '@/lib/admin/visit-tags'
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
  const definitionsQueryKey = ['auth', accountId, 'tag-definitions'] as const
  const requests = useRef(new Set<AbortController>())
  const active = useRef(true)
  const saving = useRef(false)
  const creating = useRef(false)
  const [editing, setEditing] = useState<{ visitId: string; edit: TagEdit } | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [createDraft, setCreateDraft] = useState<TagDefinitionDraft>(emptyTagDefinition())
  const [createFieldErrors, setCreateFieldErrors] = useState<Record<string, string>>({})
  const [createError, setCreateError] = useState('')
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    active.current = true
    const discard = () => {
      active.current = false
      requests.current.forEach(controller => controller.abort())
      requests.current.clear()
      void client.cancelQueries({ queryKey: ['auth', accountId, 'restaurant-visit-tags', restaurantId] })
      void client.cancelQueries({ queryKey: ['auth', accountId, 'tag-definitions'] })
      client.removeQueries({ queryKey: ['auth', accountId, 'restaurant-visit-tags', restaurantId] })
      client.removeQueries({ queryKey: ['auth', accountId, 'tag-definitions'] })
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
  const definitionsQuery = useQuery({
    queryKey: definitionsQueryKey,
    queryFn: ({ signal }) => tracked(signal => getTagDefinitions(accountId, signal), signal),
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
  const saveMutation = useMutation({
    mutationFn: ({ visitId, edit }: { visitId: string; edit: TagEdit }) =>
      tracked(signal => saveVisitTags(restaurantId, visitId, accountId, edit, signal)),
    retry: false,
    gcTime: 0,
  })
  const createMutation = useMutation({
    mutationFn: (draft: TagDefinitionDraft) => tracked(signal => createTagDefinition(accountId, prepareTagDefinition(draft), signal)),
    retry: false,
    gcTime: 0,
  })

  function begin(visit: VisitTags) {
    setError('')
    setNotice('')
    resetCreation()
    setEditing({ visitId: visit.visitId, edit: { expectedVersion: visit.version, tagCodes: visit.tags.map(tag => tag.code), reason: '' } })
  }

  function resetCreation() {
    setCreateOpen(false)
    setCreateDraft(emptyTagDefinition())
    setCreateFieldErrors({})
    setCreateError('')
  }

  async function create() {
    if (!editing || creating.current || !active.current || editing.edit.tagCodes.length >= 50) return
    const request = prepareTagDefinition(createDraft)
    const validation = validateTagDefinition(request)
    setCreateFieldErrors(validation)
    setCreateError('')
    if (Object.keys(validation).length > 0) return

    creating.current = true
    try {
      const definition = await createMutation.mutateAsync(createDraft)
      if (!active.current) return
      client.setQueryData<TagDefinitionList>(definitionsQueryKey, current => appendTagDefinitionToList(current, definition))
      client.setQueryData<RestaurantVisitTags>(queryKey, current => appendTagDefinition(current, definition))
      setEditing(current => current ? { ...current, edit: selectCreatedTag(current.edit, definition.code) } : current)
      resetCreation()
      setNotice(`새 태그 '${definition.displayName}'을 만들고 현재 방문에 선택했습니다. 방문 태그 저장을 눌러 반영해 주세요.`)
      await Promise.all([
        client.invalidateQueries({ queryKey: definitionsQueryKey }),
        client.invalidateQueries({ queryKey }),
      ])
    } catch (reason) {
      if (!active.current) return
      const serverFields = fieldErrorsFor(reason)
      setCreateFieldErrors(Object.fromEntries(Object.entries(serverFields).map(([field, message]) => [field.replace(/\[\d+\]$/u, ''), message])))
      setCreateError(messageFor(reason))
    } finally { creating.current = false }
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!editing || saving.current || !active.current || !query.data) return
    const currentVisit = query.data.items.find(visit => visit.visitId === editing.visitId)
    const validation = validateTagEdit(editing.edit, query.data.tagOptions, currentVisit?.tags.map(tag => tag.code))
    if (validation) { setError(validation); return }
    saving.current = true
    setError('')
    try {
      await saveMutation.mutateAsync(editing)
      if (!active.current) return
      setEditing(null)
      setNotice('방문 태그를 저장했습니다.')
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

  const options = definitionsQuery.data?.items ?? query.data?.tagOptions ?? []
  return <section className={styles.panel} aria-label="관리자 방문 태그 관리">
    <h2>방문 태그 관리 <span className={styles.admin}>관리자</span></h2>
    <p>자연어 검색에 사용하는 태그입니다. 여러 태그 조건은 같은 방문에 함께 있어야 검색됩니다.</p>
    <p><Link href="/admin/tag-definitions">태그 정의 관리에서 표시명·별칭과 활성 상태 수정하기 →</Link></p>
    {notice && <p role="status">{notice}</p>}
    {error && <p role="alert" className={styles.error}>{error}</p>}
    {query.isPending || definitionsQuery.isPending ? <p role="status">방문 태그를 불러오는 중…</p> : query.isError || definitionsQuery.isError ? <div>
      <p role="alert" className={styles.error}>{messageFor(query.error ?? definitionsQuery.error)}</p>
      <Button variant="secondary" disabled={query.isFetching || definitionsQuery.isFetching} onClick={() => void Promise.all([query.refetch(), definitionsQuery.refetch()])}>다시 불러오기</Button>
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
            <fieldset disabled={saveMutation.isPending}>
              <legend>이 방문의 태그 선택 ({draft.tagCodes.length}/50)</legend>
              {inactive.length > 0 && <p>비활성 태그는 유지하거나 해제할 수 있지만, 해제한 뒤 다시 추가할 수 없습니다.</p>}
              <div className={styles.options}>{[...options, ...inactive].map(tag => {
                const checked = draft.tagCodes.includes(tag.code)
                const disabled = !checked && (!options.some(option => option.code === tag.code) || draft.tagCodes.length >= 50)
                return <label key={tag.code}><input type="checkbox" checked={checked} disabled={disabled}
                  onChange={() => setEditing({ visitId: visit.visitId, edit: { ...draft, tagCodes: checked ? draft.tagCodes.filter(code => code !== tag.code) : [...draft.tagCodes, tag.code] } })} />
                  {tag.displayName}{inactive.some(item => item.code === tag.code) ? ' (비활성)' : ''}
                </label>
              })}</div>
              <div className={styles.createArea}>
                {createOpen ? <fieldset className={styles.createForm} disabled={createMutation.isPending}>
                  <legend>새 태그 정의</legend>
                  <p>새 태그를 만들면 이 방문에 바로 선택됩니다. 별칭은 줄마다 하나씩 입력해 주세요.</p>
                  <div className={styles.createGrid}>
                    <label>유형
                      <select value={createDraft.type} onChange={event => { setCreateDraft(current => ({ ...current, type: event.target.value as TagDefinitionDraft['type'] })); setCreateFieldErrors(current => ({ ...current, type: '', code: '' })) }}>
                        {TAG_DEFINITION_TYPES.map(type => <option key={type} value={type}>{type}</option>)}
                      </select>
                    </label>
                    <label>코드
                      <input value={createDraft.code} maxLength={64} placeholder={`${createDraft.type}_...`} aria-invalid={Boolean(createFieldErrors.code)} aria-describedby={createFieldErrors.code ? 'tag-definition-code-error' : undefined}
                        onChange={event => { setCreateDraft(current => ({ ...current, code: event.target.value.toUpperCase() })); setCreateFieldErrors(current => ({ ...current, code: '' })) }} />
                      {createFieldErrors.code && <small id="tag-definition-code-error" className={styles.error}>{createFieldErrors.code}</small>}
                    </label>
                    <label>표시명
                      <input value={createDraft.displayName} maxLength={100} aria-invalid={Boolean(createFieldErrors.displayName)} aria-describedby={createFieldErrors.displayName ? 'tag-definition-display-name-error' : undefined}
                        onChange={event => { setCreateDraft(current => ({ ...current, displayName: event.target.value })); setCreateFieldErrors(current => ({ ...current, displayName: '' })) }} />
                      {createFieldErrors.displayName && <small id="tag-definition-display-name-error" className={styles.error}>{createFieldErrors.displayName}</small>}
                    </label>
                    <label className={styles.aliases}>별칭 (선택)
                      <textarea rows={3} value={createDraft.aliases} maxLength={2020} aria-invalid={Boolean(createFieldErrors.aliases)} aria-describedby={createFieldErrors.aliases ? 'tag-definition-aliases-error' : undefined}
                        onChange={event => { setCreateDraft(current => ({ ...current, aliases: event.target.value })); setCreateFieldErrors(current => ({ ...current, aliases: '' })) }} />
                      {createFieldErrors.aliases && <small id="tag-definition-aliases-error" className={styles.error}>{createFieldErrors.aliases}</small>}
                    </label>
                  </div>
                  {createError && <p role="alert" className={styles.error}>{createError}</p>}
                  <div className={styles.actions}>
                    <Button disabled={createMutation.isPending || draft.tagCodes.length >= 50} onClick={() => void create()}>{createMutation.isPending ? '만드는 중…' : '태그 만들기'}</Button>
                    <Button variant="secondary" disabled={createMutation.isPending} onClick={resetCreation}>닫기</Button>
                  </div>
                  {draft.tagCodes.length >= 50 && <p>현재 방문의 태그를 하나 이상 해제한 뒤 새 태그를 만들 수 있습니다.</p>}
                </fieldset> : <Button variant="secondary" disabled={createMutation.isPending || draft.tagCodes.length >= 50} onClick={() => { setCreateOpen(true); setCreateError(''); setCreateFieldErrors({}) }}>새 태그 추가</Button>}
              </div>
              <label className={styles.reason}>수정 사유 (필수)
                <textarea required maxLength={1000} rows={3} value={draft.reason} onChange={event => setEditing({ visitId: visit.visitId, edit: { ...draft, reason: event.target.value } })} />
              </label>
              {draft.tagCodes.length === 0 && <p>저장하면 이 방문의 태그가 모두 해제됩니다.</p>}
              <div className={styles.actions}>
                <Button type="submit" disabled={saveMutation.isPending || createMutation.isPending}>{saveMutation.isPending ? '저장 중…' : '저장'}</Button>
                <Button variant="secondary" disabled={saveMutation.isPending || createMutation.isPending} onClick={() => { setEditing(null); setError(''); resetCreation() }}>취소</Button>
              </div>
            </fieldset>
          </form> : <Button variant="secondary" disabled={editing !== null || saveMutation.isPending || query.isFetching || definitionsQuery.isFetching} onClick={() => begin(visit)}>태그 수정</Button>}
        </li>
      })}</ul>}
  </section>
}

function emptyTagDefinition(): TagDefinitionDraft {
  return { code: '', type: 'MENU', displayName: '', aliases: '' }
}
