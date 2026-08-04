'use client'

import { useCallback, useEffect, useRef, useState } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import {
  ContractError,
  createParticipation,
  getParticipationDetail,
  getParticipations,
  ParticipationItem,
  participationErrorMessage,
  participationPayloadKey,
  ReportInput,
  ReportType,
  RequestKind,
  RequestStatus,
  SubmissionInput,
  TargetType,
} from '@/lib/member/participation'
import { allowedReportTypes } from '@/lib/member/participation-coordination'

import styles from './ParticipationRequestScreen.module.css'

const TARGETS: TargetType[] = ['RESTAURANT', 'CREATOR', 'VIDEO', 'VISIT_RELATIONSHIP']
const STATUSES: RequestStatus[] = ['RECEIVED', 'IN_REVIEW', 'ACCEPTED', 'REJECTED', 'COMPLETED']

export function ParticipationRequestScreen() {
  const { status: session } = useMemberSession()
  const [kind, setKind] = useState<RequestKind>('submission')
  const [targetType, setTargetType] = useState<TargetType>('RESTAURANT')
  const [targetId, setTargetId] = useState('')
  const [reportType, setReportType] = useState<ReportType>('ERROR')
  const [candidate, setCandidate] = useState<Record<string, string>>({ name: '', roadAddress: '' })
  const [description, setDescription] = useState('')
  const [evidenceUrl, setEvidenceUrl] = useState('')
  const [filter, setFilter] = useState<RequestStatus | ''>('')
  const [items, setItems] = useState<ParticipationItem[]>([])
  const [selected, setSelected] = useState<ParticipationItem | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState(false)
  const [busy, setBusy] = useState(false)
  const retry = useRef<{ fingerprint: string; key: string } | null>(null)

  const load = useCallback(async () => {
    if (session !== 'authenticated') return
    setBusy(true)
    try {
      const page = await getParticipations(kind, filter)
      setItems(page.items)
      setError(false)
      setMessage(page.items.length ? '' : '아직 접수한 요청이 없습니다.')
    } catch {
      setError(true)
      setMessage('내 요청을 불러오지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setBusy(false)
    }
  }, [filter, kind, session])

  useEffect(() => { void load() }, [load])

  function changeTarget(next: TargetType) {
    setTargetType(next)
    if (next === 'RESTAURANT') setCandidate({ name: '', roadAddress: '' })
    if (next === 'CREATOR') setCandidate({ channelUrl: '' })
    if (next === 'VIDEO') setCandidate({ videoUrl: '' })
    if (next === 'VISIT_RELATIONSHIP') setCandidate({ restaurantId: '', creatorId: '', videoId: '' })
    const nextReportTypes = allowedReportTypes(next) as ReportType[]
    if (!nextReportTypes.includes(reportType)) setReportType(nextReportTypes[0])
    retry.current = null
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    const base = { targetType, description, ...(evidenceUrl ? { evidenceUrl } : {}) }
    const payload: SubmissionInput | ReportInput = kind === 'submission'
      ? { ...base, candidate }
      : { ...base, targetId, reportType }
    const fingerprint = participationPayloadKey(kind, payload)
    if (!retry.current || retry.current.fingerprint !== fingerprint) {
      retry.current = { fingerprint, key: crypto.randomUUID() }
    }
    setBusy(true)
    setMessage('접수 중입니다...')
    setError(false)
    try {
      const created = await createParticipation(kind, payload, retry.current.key)
      retry.current = null
      setSelected(created)
      setDescription('')
      setEvidenceUrl('')
      setMessage('요청을 접수했습니다. 접수만으로 공개 데이터가 변경되거나 숨겨지지 않습니다.')
      await load()
    } catch (reason) {
      let contract: ContractError = {}
      if (reason instanceof Response) {
        try { contract = (await reason.json()) as ContractError } catch { contract = {} }
        setMessage(participationErrorMessage(reason.status, contract))
        if ((contract.code === 'DUPLICATE_OPEN_SUBMISSION' || contract.code === 'DUPLICATE_OPEN_REPORT')
          && contract.resource?.requestId) {
          setFilter('')
          try { setSelected(await getParticipationDetail(kind, contract.resource.requestId)) } catch { /* 목록에서 재확인 */ }
        }
      } else {
        setMessage('네트워크 오류가 발생했습니다. 같은 요청으로 다시 시도해 주세요.')
      }
      setError(true)
    } finally {
      setBusy(false)
    }
  }

  async function openDetail(item: ParticipationItem) {
    setBusy(true)
    try {
      setSelected(await getParticipationDetail(kind, item.requestId))
    } catch {
      setError(true)
      setMessage('요청 상세를 불러오지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  if (session === 'loading') return <p role="status">로그인 상태를 확인하고 있습니다.</p>
  if (session === 'anonymous') return <p role="alert">제보와 신고는 로그인 후 이용할 수 있습니다.</p>

  return <section className={styles.screen}>
    <header>
      <h1>내 제보·신고</h1>
      <p>새 정보는 제보하고, 기존 정보의 문제는 신고해 주세요. 하루 합산 5건까지 접수할 수 있습니다.</p>
    </header>

    <div className={styles.tabs} role="tablist" aria-label="요청 종류">
      <button type="button" role="tab" aria-selected={kind === 'submission'} onClick={() => { setKind('submission'); setSelected(null); retry.current = null }}>제보: 새 정보 제안</button>
      <button type="button" role="tab" aria-selected={kind === 'report'} onClick={() => { setKind('report'); setSelected(null); retry.current = null }}>신고: 기존 정보 문제</button>
    </div>

    <form className={styles.form} onSubmit={event => void submit(event)}>
      <h2>{kind === 'submission' ? '새 제보 접수' : '새 신고 접수'}</h2>
      <label>대상 유형
        <select value={targetType} onChange={event => changeTarget(event.target.value as TargetType)}>
          {TARGETS.map(value => <option key={value}>{value}</option>)}
        </select>
      </label>
      {kind === 'submission'
        ? Object.keys(candidate).map(field => <label key={field}>{field}
          <input required value={candidate[field]} onChange={event => { setCandidate({ ...candidate, [field]: event.target.value }); retry.current = null }} />
        </label>)
        : <>
          <label>대상 식별자<input required value={targetId} onChange={event => { setTargetId(event.target.value); retry.current = null }} /></label>
          <label>신고 유형<select value={reportType} onChange={event => { setReportType(event.target.value as ReportType); retry.current = null }}>
            {(allowedReportTypes(targetType) as ReportType[]).map(value => <option key={value}>{value}</option>)}
          </select></label>
        </>}
      <label>설명 <span className={styles.muted}>10~2000자</span>
        <textarea required minLength={10} maxLength={2000} rows={6} value={description} onChange={event => { setDescription(event.target.value); retry.current = null }} />
      </label>
      <label>근거 URL <span className={styles.muted}>선택, HTTPS만 허용</span>
        <input type="url" pattern="https://.*" value={evidenceUrl} onChange={event => { setEvidenceUrl(event.target.value); retry.current = null }} />
      </label>
      <p className={styles.muted}>개인정보를 입력하지 마세요. 파일 첨부와 익명 접수는 지원하지 않습니다.</p>
      <Button disabled={busy}>{busy ? '처리 중...' : '접수하기'}</Button>
    </form>

    <section>
      <div className={styles.filters}>
        <h2>내 요청 목록</h2>
        <label>상태 <select value={filter} onChange={event => setFilter(event.target.value as RequestStatus | '')}>
          <option value="">전체</option>
          {STATUSES.map(value => <option key={value}>{value}</option>)}
        </select></label>
        <Button variant="secondary" disabled={busy} onClick={() => void load()}>새로고침</Button>
      </div>
      <ul className={styles.list}>
        {items.map(item => <li key={item.requestId}>
          <button className={styles.item} type="button" onClick={() => void openDetail(item)}>
            <strong>{item.targetType}</strong> · {item.status}<br />
            <span>{item.description}</span>
          </button>
        </li>)}
      </ul>
    </section>

    {selected ? <section className={styles.detail} aria-live="polite">
      <h2>요청 상세</h2>
      <p><strong>상태:</strong> {selected.status}</p>
      <p><strong>대상:</strong> {selected.targetType}</p>
      <p>{selected.description}</p>
      {selected.memberReason ? <p><strong>처리 사유:</strong> {selected.memberReason}</p> : null}
      {selected.evidenceUrl ? <p><a href={selected.evidenceUrl} target="_blank" rel="noreferrer">근거 URL 열기</a></p> : null}
      {selected.status === 'ACCEPTED' ? <p>승인됐으며 실제 반영을 기다리고 있습니다.</p> : null}
    </section> : null}
    {message ? <p className={error ? styles.error : undefined} role={error ? 'alert' : 'status'}>{message}</p> : null}
  </section>
}
