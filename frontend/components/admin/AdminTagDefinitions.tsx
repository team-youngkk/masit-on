'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import { AdminApiError, messageFor } from '@/lib/admin/api'
import { changeTagDefinitionStatus, getManagedTagDefinitions, getTagDefinitionHistory, updateTagDefinition } from '@/lib/admin/visit-tags'
import { adminTagScope, type TagDefinition } from '@/lib/admin/visit-tags-coordination'
import styles from './AdminTagDefinitions.module.css'

type Draft = { displayName: string; aliases: string; reason: string }

export function AdminTagDefinitions() {
  const { status: sessionStatus, session } = useMemberSession()
  const accountId = adminTagScope(sessionStatus, session)
  if (!accountId) return null
  return <TagDefinitionsContent key={accountId} accountId={accountId} />
}

function TagDefinitionsContent({ accountId }: { accountId: string }) {
  const client = useQueryClient()
  const [status, setStatus] = useState('ALL')
  const [page, setPage] = useState(1)
  const [selectedCode, setSelectedCode] = useState<string | null>(null)
  const [historyPage, setHistoryPage] = useState(1)
  const [conflict, setConflict] = useState(false)
  const preserveDraft = useRef(false)
  const requests = useRef(new Set<AbortController>())
  const active = useRef(true)
  const [draft, setDraft] = useState<Draft>({ displayName: '', aliases: '', reason: '' })
  const [notice, setNotice] = useState('')
  const queryKey = ['auth', accountId, 'tag-definition-management', status, page] as const
  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => tracked(requestSignal => getManagedTagDefinitions(accountId, status, page, requestSignal), signal),
    retry: false,
    gcTime: 0,
  })
  const selected = query.data?.items.find(item => item.code === selectedCode) ?? null
  const history = useQuery({
    queryKey: ['auth', accountId, 'tag-definition-history', selectedCode, historyPage],
    queryFn: ({ signal }) => tracked(requestSignal => getTagDefinitionHistory(accountId, selectedCode!, historyPage, requestSignal), signal),
    enabled: Boolean(selectedCode),
    retry: false,
    gcTime: 0,
  })
  useEffect(() => {
    if (preserveDraft.current) { preserveDraft.current = false; return }
    if (selected) setDraft({ displayName: selected.displayName, aliases: selected.aliases.join('\n'), reason: '' })
  }, [selected?.code, selected?.version])
  useEffect(() => {
    active.current = true
    const discard = () => {
      active.current = false
      requests.current.forEach(controller => controller.abort())
      requests.current.clear()
      void client.cancelQueries({ queryKey: ['auth', accountId, 'tag-definition-management'] })
      void client.cancelQueries({ queryKey: ['auth', accountId, 'tag-definition-history'] })
      void client.cancelQueries({ queryKey: ['auth', accountId, 'tag-definitions'] })
      client.removeQueries({ queryKey: ['auth', accountId, 'tag-definition-management'] })
      client.removeQueries({ queryKey: ['auth', accountId, 'tag-definition-history'] })
      client.removeQueries({ queryKey: ['auth', accountId, 'tag-definitions'] })
    }
    return discard
  }, [accountId, client])

  async function tracked<T>(request: (signal: AbortSignal) => Promise<T>, signal?: AbortSignal): Promise<T> {
    const controller = new AbortController()
    requests.current.add(controller)
    try { return await request(signal ? AbortSignal.any([signal, controller.signal]) : controller.signal) }
    finally { requests.current.delete(controller) }
  }

  const updateMutation = useMutation({
    mutationFn: ({ definition, draft }: { definition: TagDefinition; draft: Draft }) => tracked(signal => updateTagDefinition(accountId, definition.code, {
      expectedVersion: definition.version,
      displayName: draft.displayName.trim(),
      aliases: draft.aliases.split(/\r?\n/u).map(value => value.trim()).filter(Boolean),
      reason: draft.reason.trim(),
    }, signal)),
    onSuccess: async definition => { if (!active.current) return; setConflict(false); setNotice(`${definition.displayName} 태그를 수정했습니다.`); await refresh(definition.code) },
    onError: error => { if (active.current) setConflict(error instanceof AdminApiError && error.status === 409) },
  })
  const statusMutation = useMutation({
    mutationFn: ({ definition, reason }: { definition: TagDefinition; reason: string }) => tracked(signal => changeTagDefinitionStatus(accountId, definition.code, {
      expectedVersion: definition.version,
      status: definition.status === 'ACTIVE' ? 'DEPRECATED' : 'ACTIVE',
      reason: reason.trim(),
    }, signal)),
    onSuccess: async definition => { if (!active.current) return; setConflict(false); setNotice(`${definition.displayName} 태그를 ${definition.status === 'ACTIVE' ? '활성화' : '비활성화'}했습니다.`); await refresh(definition.code) },
    onError: error => { if (active.current) setConflict(error instanceof AdminApiError && error.status === 409) },
  })
  async function refresh(code: string) {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['auth', accountId, 'tag-definition-management'] }),
      client.invalidateQueries({ queryKey: ['auth', accountId, 'tag-definitions'] }),
      client.invalidateQueries({ queryKey: ['auth', accountId, 'tag-definition-history', code] }),
    ])
  }
  function validDraft() { return Boolean(draft.displayName.trim() && draft.reason.trim() && draft.reason.trim().length <= 1000) }
  async function reloadLatest() {
    preserveDraft.current = true
    setConflict(false)
    setNotice('입력값을 유지한 채 최신 버전을 불러왔습니다. 변경 내용을 다시 확인해 주세요.')
    await Promise.all([query.refetch(), history.refetch()])
  }

  return <div className={styles.layout}>
    <section className={styles.list} aria-label="태그 정의 목록">
      <p>변경한 활성 태그 용어는 자연어 검색에 최대 30초 안에 반영됩니다.</p>
      <div className={styles.toolbar}>
        <label>상태 <select value={status} onChange={event => { setStatus(event.target.value); setPage(1); setSelectedCode(null) }}>
          <option value="ALL">전체</option><option value="ACTIVE">활성</option><option value="DEPRECATED">비활성</option>
        </select></label>
        <span>총 {query.data?.page.totalElements ?? 0}개</span>
      </div>
      {query.isPending ? <p>태그를 불러오는 중…</p> : query.isError ? <p role="alert" className={styles.error}>{messageFor(query.error)}</p> :
        <ul>{query.data.items.map(item => <li key={item.code}>
          <button type="button" className={item.code === selectedCode ? styles.selected : styles.row} onClick={() => { setSelectedCode(item.code); setHistoryPage(1); setConflict(false); setNotice('') }}>
            <strong>{item.displayName}</strong><code>{item.code}</code><span className={item.status === 'ACTIVE' ? styles.active : styles.deprecated}>{item.status === 'ACTIVE' ? '활성' : '비활성'}</span>
          </button>
        </li>)}</ul>}
      {query.data && query.data.page.totalPages > 1 && <div className={styles.pagination}>
        <Button variant="secondary" disabled={page <= 1} onClick={() => setPage(value => value - 1)}>이전</Button>
        <span>{page} / {query.data.page.totalPages}</span>
        <Button variant="secondary" disabled={page >= query.data.page.totalPages} onClick={() => setPage(value => value + 1)}>다음</Button>
      </div>}
    </section>
    <section className={styles.detail} aria-label="태그 정의 편집">
      {!selected ? <p>목록에서 수정할 태그를 선택해 주세요.</p> : <>
        <header><div><h2>{selected.displayName}</h2><code>{selected.code}</code></div><span className={selected.status === 'ACTIVE' ? styles.active : styles.deprecated}>{selected.status === 'ACTIVE' ? '활성' : '비활성'}</span></header>
        <p>유형 {selected.type} · 출처 {selected.source} · 버전 {selected.version}</p>
        <label>표시명<input maxLength={100} value={draft.displayName} onChange={event => setDraft(value => ({ ...value, displayName: event.target.value }))} /></label>
        <label>별칭<textarea rows={5} maxLength={2020} value={draft.aliases} onChange={event => setDraft(value => ({ ...value, aliases: event.target.value }))} /><small>줄마다 하나씩 최대 20개</small></label>
        <label>변경 사유<textarea rows={3} maxLength={1000} value={draft.reason} onChange={event => setDraft(value => ({ ...value, reason: event.target.value }))} /></label>
        {(updateMutation.isError || statusMutation.isError) && <p role="alert" className={styles.error}>{messageFor(updateMutation.error ?? statusMutation.error)}</p>}
        {conflict && <Button variant="secondary" onClick={() => void reloadLatest()}>입력값을 유지하고 최신 버전 불러오기</Button>}
        {notice && <p role="status">{notice}</p>}
        <div className={styles.actions}>
          <Button disabled={!validDraft() || updateMutation.isPending || statusMutation.isPending} onClick={() => updateMutation.mutate({ definition: selected, draft })}>내용 저장</Button>
          <Button variant="secondary" disabled={!draft.reason.trim() || updateMutation.isPending || statusMutation.isPending} onClick={() => statusMutation.mutate({ definition: selected, reason: draft.reason })}>{selected.status === 'ACTIVE' ? '비활성화' : '다시 활성화'}</Button>
        </div>
        <h3>변경 이력</h3>
        {history.isPending ? <p>이력을 불러오는 중…</p> : history.isError ? <p className={styles.error}>이력을 불러오지 못했습니다.</p> : history.data?.items.length ? <><ol className={styles.history}>{history.data.items.map(item => <li key={item.id}><strong>{actionLabel(item.action)}</strong> · 버전 {item.version}<br /><span>{item.reason} · {new Date(item.changedAt).toLocaleString('ko-KR')}</span></li>)}</ol><div className={styles.pagination}><Button variant="secondary" disabled={historyPage <= 1} onClick={() => setHistoryPage(value => value - 1)}>이전 이력</Button><span>{historyPage} / {history.data.page.totalPages}</span><Button variant="secondary" disabled={!history.data.page.hasNext} onClick={() => setHistoryPage(value => value + 1)}>다음 이력</Button></div></> : <p>아직 변경 이력이 없습니다.</p>}
      </>}
    </section>
  </div>
}

function actionLabel(action: string) {
  return action === 'UPDATE' ? '내용 수정' : action === 'DEPRECATE' ? '비활성화' : '재활성화'
}
