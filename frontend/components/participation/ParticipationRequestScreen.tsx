'use client'

import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import { PageShell, SectionHeader } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'
import { memberLoginHref } from '@/lib/member/auth-navigation'
import {
  ContractError,
  createParticipation,
  getParticipationDetail,
  getParticipations,
  ParticipationItem,
  parseParticipationError,
  participationErrorMessage,
  participationPayloadKey,
  ReportInput,
  ReportType,
  RequestKind,
  RequestStatus,
  SubmissionInput,
  TargetType,
} from '@/lib/member/participation'
import {
  allowedReportTypes,
  createParticipationDetailCoordinator,
  participationCandidateFieldLabel,
  participationDuplicateRequestId,
  participationReportTypeLabel,
  participationStatusLabel,
  participationTargetDetails,
  participationTargetSummary,
  participationTargetTypeLabel,
  isCurrentParticipationDetailRequest,
  updateParticipationListQuery,
} from '@/lib/member/participation-coordination'

import styles from './ParticipationRequestScreen.module.css'

const RETURN_TO = '/me/requests'

const TARGETS: TargetType[] = ['RESTAURANT', 'CREATOR', 'VIDEO', 'VISIT_RELATIONSHIP']
const STATUSES: RequestStatus[] = ['RECEIVED', 'IN_REVIEW', 'ACCEPTED', 'REJECTED', 'COMPLETED']
const TAB_ORDER: RequestKind[] = ['submission', 'report']

type SubmitNotice = { text: string; isError: boolean; traceId?: string }

type ParticipationRequestScreenProps = {
  view?: 'new' | 'history'
  initialKind?: RequestKind
  initialTargetType?: TargetType
  initialTargetId?: string
  loginReturnTo?: string
}

export function ParticipationRequestScreen({
  view = 'new',
  initialKind = 'submission',
  initialTargetType = 'RESTAURANT',
  initialTargetId = '',
  loginReturnTo = RETURN_TO,
}: ParticipationRequestScreenProps) {
  const { status: session } = useMemberSession()
  const [kind, setKind] = useState<RequestKind>(initialKind)
  const [targetType, setTargetType] = useState<TargetType>(initialTargetType)
  const [targetId, setTargetId] = useState(initialTargetId)
  const [reportType, setReportType] = useState<ReportType>('ERROR')
  const [candidate, setCandidate] = useState<Record<string, string>>({ name: '', roadAddress: '' })
  const [description, setDescription] = useState('')
  const [evidenceUrl, setEvidenceUrl] = useState('')
  const [filter, setFilter] = useState<RequestStatus | ''>('')
  const [items, setItems] = useState<ParticipationItem[]>([])
  const [pageNumber, setPageNumber] = useState(1)
  const [totalPages, setTotalPages] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [selected, setSelected] = useState<ParticipationItem | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState(false)
  const [listError, setListError] = useState(false)
  const [errorTraceId, setErrorTraceId] = useState<string | undefined>(undefined)
  const [unauthorized, setUnauthorized] = useState(false)
  const [submitNotice, setSubmitNotice] = useState<SubmitNotice | null>(null)
  const [busy, setBusy] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const retry = useRef<{ fingerprint: string; key: string } | null>(null)
  const listRequest = useRef(0)
  const detailRequest = useRef(createParticipationDetailCoordinator(initialKind))
  const submissionTabRef = useRef<HTMLButtonElement>(null)
  const reportTabRef = useRef<HTMLButtonElement>(null)

  const load = useCallback(async () => {
    if (session !== 'authenticated') return
    const request = ++listRequest.current
    setBusy(true)
    try {
      const page = await getParticipations(kind, filter, pageNumber)
      if (request !== listRequest.current) return
      setItems(page.items)
      setPageNumber(page.page.number)
      setTotalPages(page.page.totalPages)
      setHasNext(page.page.hasNext)
      setError(false)
      setListError(false)
      setErrorTraceId(undefined)
      setUnauthorized(false)
      setMessage('')
    } catch (reason) {
      const parsed = await parseParticipationError(reason)
      if (request !== listRequest.current) return
      setErrorTraceId(parsed?.contract.traceId)
      if (parsed?.status === 401) {
        setUnauthorized(true)
        setError(false)
        setListError(false)
        setMessage('')
      } else {
        setUnauthorized(false)
        setError(true)
        setListError(true)
        setMessage(parsed?.contract.message || '내 요청을 불러오지 못했습니다. 다시 시도해 주세요.')
      }
    } finally {
      if (request === listRequest.current) {
        setBusy(false)
        setLoaded(true)
      }
    }
  }, [filter, kind, pageNumber, session])

  useEffect(() => {
    if (view === 'history') void load()
  }, [load, view])

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

  function resetPageForFilters(nextKind: RequestKind, nextFilter: RequestStatus | '') {
    const next = updateParticipationListQuery(
      { kind, status: filter, page: pageNumber },
      { kind: nextKind, status: nextFilter },
    )
    setPageNumber(next.page)
  }

  function switchTab(nextKind: RequestKind) {
    if (nextKind === kind) return
    detailRequest.current.switchKind(nextKind)
    resetPageForFilters(nextKind, filter)
    setKind(nextKind)
    setSelected(null)
    setSubmitNotice(null)
    retry.current = null
  }

  function handleTabListKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    const currentIndex = TAB_ORDER.indexOf(kind)
    let nextIndex: number | null = null
    if (event.key === 'ArrowLeft') nextIndex = (currentIndex - 1 + TAB_ORDER.length) % TAB_ORDER.length
    else if (event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % TAB_ORDER.length
    else if (event.key === 'Home') nextIndex = 0
    else if (event.key === 'End') nextIndex = TAB_ORDER.length - 1
    if (nextIndex === null) return
    event.preventDefault()
    const nextKind = TAB_ORDER[nextIndex]
    switchTab(nextKind)
    ;(nextKind === 'submission' ? submissionTabRef : reportTabRef).current?.focus()
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
    setSubmitNotice({ text: '접수 중입니다...', isError: false })
    try {
      const created = await createParticipation(kind, payload, retry.current.key)
      retry.current = null
      setSelected(view === 'history' ? created : null)
      setDescription('')
      setEvidenceUrl('')
      setSubmitNotice({ text: '요청을 접수했습니다. 접수만으로 공개 데이터가 변경되거나 숨겨지지 않습니다.', isError: false })
      if (view === 'history') {
        if (pageNumber === 1) await load()
        else setPageNumber(1)
      }
    } catch (reason) {
      const parsed = await parseParticipationError(reason)
      if (parsed?.status === 401) {
        setUnauthorized(true)
        setError(false)
        setMessage('')
        setErrorTraceId(parsed.contract.traceId)
        setSubmitNotice(null)
      } else if (parsed) {
        const contract: ContractError = parsed.contract
        setSubmitNotice({ text: participationErrorMessage(parsed.status, contract), isError: true, traceId: contract.traceId })
        const duplicateRequestId = participationDuplicateRequestId(contract)
        if (duplicateRequestId) {
          setFilter('')
          setPageNumber(1)
          try {
            await detailRequest.current.load(
              kind,
              duplicateRequestId,
              getParticipationDetail,
              setSelected,
            )
          } catch { /* 목록에서 재확인 */ }
        }
      } else {
        setSubmitNotice({ text: '네트워크 오류가 발생했습니다. 같은 요청으로 다시 시도해 주세요.', isError: true })
      }
    } finally {
      setBusy(false)
    }
  }

  async function openDetail(item: ParticipationItem) {
    const request = detailRequest.current.begin(kind)
    setBusy(true)
    try {
      const detail = await getParticipationDetail(kind, item.requestId)
      if (!request.isCurrent()) return
      setSelected(detail)
      setError(false)
      setErrorTraceId(undefined)
      setUnauthorized(false)
      setMessage('')
    } catch (reason) {
      const parsed = await parseParticipationError(reason)
      if (!request.isCurrent()) return
      setErrorTraceId(parsed?.contract.traceId)
      if (parsed?.status === 401) {
        setUnauthorized(true)
        setError(false)
        setMessage('')
      } else {
        setUnauthorized(false)
        setError(true)
        setMessage(parsed?.contract.message || '요청 상세를 불러오지 못했습니다.')
      }
    } finally {
      if (request.isCurrent()) setBusy(false)
    }
  }

  if (session === 'loading') return <PageShell title={view === 'new' ? '제보·신고 접수' : '내 제보·신고 내역'}><StatePanel title="로그인 상태를 확인하고 있습니다." /></PageShell>
  if (session === 'anonymous') return (
    <PageShell title={view === 'new' ? '제보·신고 접수' : '내 제보·신고 내역'}><StatePanel tone="warning" title="제보와 신고는 로그인 후 이용할 수 있습니다." actions={<Link href={memberLoginHref(loginReturnTo)}>로그인하기</Link>} /></PageShell>
  )

  return <PageShell
    className={styles.screen}
    title={view === 'new' ? '제보·신고 접수' : '내 제보·신고 내역'}
    description={view === 'new' ? '새 정보는 제보하고, 기존 정보의 문제는 신고해 주세요. 하루 합산 5건까지 접수할 수 있습니다.' : '제보와 신고의 처리 상태와 답변을 확인할 수 있습니다.'}
  >

    <div className={styles.tabs} role="tablist" aria-label={view === 'new' ? '요청 종류' : '내역 종류'} onKeyDown={handleTabListKeyDown}>
      <button id="tab-submission" ref={submissionTabRef} type="button" role="tab" aria-selected={kind === 'submission'} aria-controls={view === 'new' ? 'participation-tabpanel' : 'participation-history-panel'} onClick={() => switchTab('submission')}>{view === 'new' ? '제보: 새 정보 제안' : '제보 내역'}</button>
      <button id="tab-report" ref={reportTabRef} type="button" role="tab" aria-selected={kind === 'report'} aria-controls={view === 'new' ? 'participation-tabpanel' : 'participation-history-panel'} onClick={() => switchTab('report')}>{view === 'new' ? '신고: 기존 정보 문제' : '신고 내역'}</button>
    </div>

    {view === 'new' ? <div id="participation-tabpanel" role="tabpanel" aria-labelledby={kind === 'submission' ? 'tab-submission' : 'tab-report'} className={styles.tabpanel}>
    <p><Link href="/me/requests">내 제보·신고 내역 확인</Link></p>
    <form className={styles.form} onSubmit={event => void submit(event)}>
      <SectionHeader title={kind === 'submission' ? '새 제보 접수' : '새 신고 접수'} level={2} />
      <label>대상 유형
        <select value={targetType} onChange={event => changeTarget(event.target.value as TargetType)}>
          {TARGETS.map(value => <option key={value} value={value}>{participationTargetTypeLabel(value)}</option>)}
        </select>
      </label>
      {kind === 'submission'
        ? Object.keys(candidate).map(field => <label key={field}>{participationCandidateFieldLabel(field)}
          <input required value={candidate[field]} onChange={event => { setCandidate({ ...candidate, [field]: event.target.value }); retry.current = null }} />
        </label>)
        : <>
          <label>대상 식별자<input required value={targetId} onChange={event => { setTargetId(event.target.value); retry.current = null }} /></label>
          <label>신고 유형<select value={reportType} onChange={event => { setReportType(event.target.value as ReportType); retry.current = null }}>
            {(allowedReportTypes(targetType) as ReportType[]).map(value => <option key={value} value={value}>{participationReportTypeLabel(value)}</option>)}
          </select></label>
        </>}
      <label>설명 <span className={styles.muted}>10~2000자</span>
        <textarea required minLength={10} maxLength={2000} rows={6} value={description} onChange={event => { setDescription(event.target.value); retry.current = null }} />
      </label>
      <label>근거 URL <span className={styles.muted}>선택, HTTPS만 허용</span>
        <input type="url" pattern="https://.*" value={evidenceUrl} onChange={event => { setEvidenceUrl(event.target.value); retry.current = null }} />
      </label>
      <p className={styles.muted}>개인정보를 입력하지 마세요. 파일 첨부와 익명 접수는 지원하지 않습니다.</p>
      <Button type="submit" disabled={busy}>{busy ? '처리 중...' : '접수하기'}</Button>
    </form>

    {submitNotice ? (
      <p className={submitNotice.isError ? styles.error : undefined} role={submitNotice.isError ? 'alert' : 'status'}>
        {submitNotice.text}
        {submitNotice.traceId ? <span className={styles.traceId}>traceId: {submitNotice.traceId}</span> : null}
      </p>
    ) : null}

    </div> : <section id="participation-history-panel" role="tabpanel" aria-labelledby={kind === 'submission' ? 'tab-submission' : 'tab-report'}>
      <div className={styles.filters}>
        <h2>{kind === 'submission' ? '내 제보 목록' : '내 신고 목록'}</h2>
        <Link href="/me/requests/new">새 제보·신고하기</Link>
        <label>상태 <select value={filter} onChange={event => {
          const nextFilter = event.target.value as RequestStatus | ''
          resetPageForFilters(kind, nextFilter)
          setFilter(nextFilter)
        }}>
          <option value="">전체</option>
          {STATUSES.map(value => <option key={value} value={value}>{participationStatusLabel(value)}</option>)}
        </select></label>
        <Button variant="secondary" disabled={busy} onClick={() => void load()}>새로고침</Button>
      </div>
      {unauthorized ? (
        <p role="alert">
          로그인이 필요합니다. <Link href={memberLoginHref(loginReturnTo)}>로그인하기</Link>
          {errorTraceId ? <span className={styles.traceId}>traceId: {errorTraceId}</span> : null}
        </p>
      ) : loaded && items.length === 0 && !listError ? (
        <p className={styles.muted} role="status">아직 접수한 요청이 없습니다.</p>
      ) : (
        <ul className={styles.list}>
          {items.map(item => <li key={item.requestId}>
            <button className={styles.item} type="button" onClick={() => void openDetail(item)}>
              <strong>{participationTargetSummary(item)}</strong> · {participationStatusLabel(item.status)}<br />
              <span>{item.description}</span>
            </button>
          </li>)}
        </ul>
      )}
      <nav className={styles.actions} aria-label="내 요청 페이지">
        <Button variant="secondary" disabled={busy || pageNumber <= 1} onClick={() => setPageNumber(pageNumber - 1)}>이전</Button>
        <span>{pageNumber} / {Math.max(totalPages, 1)} 페이지</span>
        <Button variant="secondary" disabled={busy || !hasNext} onClick={() => setPageNumber(pageNumber + 1)}>다음</Button>
      </nav>
    </section>}

    {selected ? <section className={styles.detail} aria-live="polite">
      <h2>요청 상세</h2>
      <p><strong>상태:</strong> {participationStatusLabel(selected.status)}</p>
      <p><strong>대상:</strong> {participationTargetTypeLabel(selected.targetType)}</p>
      {participationTargetDetails(selected).map(([label, value]) => <p key={label}><strong>{label}:</strong> {value}</p>)}
      <p>{selected.description}</p>
      {selected.memberReason ? <p><strong>처리 사유:</strong> {selected.memberReason}</p> : null}
      {selected.evidenceUrl ? <p><a href={selected.evidenceUrl} target="_blank" rel="noreferrer">근거 URL 열기</a></p> : null}
      {selected.status === 'ACCEPTED' ? <p>승인됐으며 실제 반영을 기다리고 있습니다.</p> : null}
      {kind === 'report' && selected.targetId && (selected.targetType === 'RESTAURANT' || selected.targetType === 'CREATOR')
        ? <p>
          <Link href={selected.targetType === 'RESTAURANT'
            ? `/restaurants/${encodeURIComponent(selected.targetId)}`
            : `/creators/${encodeURIComponent(selected.targetId)}`}>
            관련 데이터 보러 가기
          </Link>
        </p>
        : null}
    </section> : null}
    {message ? <p className={error ? styles.error : undefined} role={error ? 'alert' : 'status'}>
      {message}
      {errorTraceId ? <span className={styles.traceId}>traceId: {errorTraceId}</span> : null}
    </p> : null}
    </PageShell>
}
