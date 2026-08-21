'use client'

import { useCallback, useEffect, useRef, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { AdminApiError, messageFor } from '@/lib/admin/api'
import {
  AdminActionType,
  AdminParticipationItem,
  AdminParticipationKind,
  AdminParticipationStatus,
  AdminTargetType,
  getAdminParticipationDetail,
  getAdminParticipations,
  updateAdminParticipationStatus,
} from '@/lib/admin/participation'
import {
  allowedNextStatuses,
  refreshAfterTransitionConflict,
  updateAdminParticipationQuery,
  validateStatusUpdate,
} from '@/lib/admin/participation-coordination'

import styles from './AdminParticipationScreen.module.css'

const STATUSES: AdminParticipationStatus[] = [
  'RECEIVED', 'IN_REVIEW', 'ACCEPTED', 'REJECTED', 'COMPLETED',
]
const TARGET_TYPES: AdminTargetType[] = ['RESTAURANT', 'CREATOR', 'VIDEO', 'VISIT_RELATIONSHIP']
const ACTION_TYPES: AdminActionType[] = ['CREATED', 'UPDATED', 'HIDDEN']
const STATUS_LABELS: Record<AdminParticipationStatus, string> = {
  RECEIVED: '접수', IN_REVIEW: '검토 중', ACCEPTED: '승인', REJECTED: '반려', COMPLETED: '처리 완료',
}
const TARGET_TYPE_LABELS: Record<AdminTargetType, string> = {
  RESTAURANT: '맛집',
  CREATOR: '유튜버',
  VIDEO: '영상',
  VISIT_RELATIONSHIP: '방문 관계',
}
const ACTION_TYPE_LABELS: Record<AdminActionType, string> = {
  CREATED: '등록',
  UPDATED: '수정',
  HIDDEN: '숨김',
}
const REPORT_TYPE_LABELS: Record<string, string> = {
  ERROR: '정보 오류',
  CLOSED: '폐업',
  UNAVAILABLE: '이용 불가',
  WRONG_RELATIONSHIP: '잘못된 연결',
  INAPPROPRIATE_CONTENT: '부적절한 콘텐츠',
}
const CANDIDATE_FIELD_LABELS: Record<string, string> = {
  name: '이름',
  roadAddress: '도로명 주소',
  channelUrl: '채널 URL',
  videoUrl: '영상 URL',
  restaurantId: '맛집 ID',
  creatorId: '유튜버 ID',
  videoId: '영상 ID',
}

function participationStatusTone(status: AdminParticipationStatus): 'success' | 'neutral' | 'warning' | 'danger' | 'info' {
  if (status === 'ACCEPTED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'IN_REVIEW') return 'warning'
  if (status === 'RECEIVED') return 'info'
  return 'neutral'
}

function targetDetails(item: AdminParticipationItem): Array<[string, string]> {
  if (item.candidate) {
    return Object.entries(item.candidate)
      .filter((entry): entry is [string, string | number | boolean] =>
        ['string', 'number', 'boolean'].includes(typeof entry[1]))
      .map(([key, value]) => [CANDIDATE_FIELD_LABELS[key] ?? key, String(value)])
  }
  return [
    ...(item.targetId ? [['대상 식별자', item.targetId] as [string, string]] : []),
    ...(item.reportType ? [['신고 유형', REPORT_TYPE_LABELS[item.reportType] ?? item.reportType] as [string, string]] : []),
  ]
}

function resultSummary(result: AdminParticipationItem['result']): string {
  return result
    ? `${ACTION_TYPE_LABELS[result.actionType]} · ${TARGET_TYPE_LABELS[result.targetType]} · ${result.targetId}`
    : '없음'
}

export function AdminParticipationScreen() {
  const [kind, setKind] = useState<AdminParticipationKind>('submission')
  const [status, setStatus] = useState<AdminParticipationStatus | ''>('')
  const [targetType, setTargetType] = useState<AdminTargetType | ''>('')
  const [pageNumber, setPageNumber] = useState(1)
  const [totalPages, setTotalPages] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [items, setItems] = useState<AdminParticipationItem[]>([])
  const [selected, setSelected] = useState<AdminParticipationItem | null>(null)
  const [memberReason, setMemberReason] = useState('')
  const [internalNote, setInternalNote] = useState('')
  const [actionConfirmed, setActionConfirmed] = useState(false)
  const [actionType, setActionType] = useState<AdminActionType | ''>('')
  const [resultTargetType, setResultTargetType] = useState<AdminTargetType | ''>('')
  const [resultTargetId, setResultTargetId] = useState('')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState(false)
  const listRequestId = useRef(0)
  const detailRequestId = useRef(0)

  const loadList = useCallback(async () => {
    const requestId = ++listRequestId.current
    const response = await getAdminParticipations(kind, pageNumber, status, targetType)
    if (requestId === listRequestId.current) {
      setItems(response.items)
      setPageNumber(response.page.number)
      setTotalPages(response.page.totalPages)
      setHasNext(response.page.hasNext)
    }
    return response
  }, [kind, pageNumber, status, targetType])

  async function refreshList() {
    setBusy(true)
    try {
      const response = await loadList()
      setError(false)
      setNotice(response.items.length ? '최신 목록을 불러왔습니다.' : '조건에 맞는 대기 요청이 없습니다.')
    } catch (reason) {
      setError(true)
      setNotice(messageFor(reason))
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => {
    let active = true
    setBusy(true)
    void loadList()
      .then((response) => {
        if (!active) return
        setError(false)
        setNotice(response.items.length ? '' : '조건에 맞는 대기 요청이 없습니다.')
      })
      .catch((reason) => {
        if (!active) return
        setError(true)
        setNotice(messageFor(reason))
      })
      .finally(() => { if (active) setBusy(false) })
    return () => { active = false }
  }, [loadList])

  function resetReviewForm(item: AdminParticipationItem) {
    setMemberReason(item.memberReason ?? '')
    setInternalNote(item.internalNote ?? '')
    setActionConfirmed(false)
    setActionType(item.result?.actionType ?? '')
    setResultTargetType(item.result?.targetType ?? item.targetType)
    setResultTargetId(item.result?.targetId ?? item.targetId ?? '')
  }

  async function openDetail(item: AdminParticipationItem) {
    const requestId = ++detailRequestId.current
    setBusy(true)
    setNotice('')
    try {
      const detail = await getAdminParticipationDetail(kind, item.requestId)
      if (requestId !== detailRequestId.current) return
      setSelected(detail)
      resetReviewForm(detail)
      setError(false)
    } catch (reason) {
      setError(true)
      setNotice(messageFor(reason))
    } finally {
      setBusy(false)
    }
  }

  function changeQuery(change: { kind?: AdminParticipationKind; status?: AdminParticipationStatus | ''; targetType?: AdminTargetType | '' }) {
    const next = updateAdminParticipationQuery(
      { kind, status, targetType, page: pageNumber },
      change,
    )
    setKind(next.kind)
    setStatus(next.status as AdminParticipationStatus | '')
    setTargetType(next.targetType as AdminTargetType | '')
    setPageNumber(next.page)
    detailRequestId.current += 1
    setSelected(null)
  }

  async function changeStatus(nextStatus: AdminParticipationStatus) {
    if (!selected) return
    const errors = validateStatusUpdate({
      status: nextStatus,
      memberReason,
      actionConfirmed,
      actionType,
      targetType: resultTargetType,
      targetId: resultTargetId,
    })
    if (errors.length) {
      setError(true)
      setNotice(errors.join(' '))
      return
    }

    setBusy(true)
    setError(false)
    setNotice('상태를 저장하고 있습니다.')
    try {
      const updated = await updateAdminParticipationStatus(kind, selected.requestId, {
        status: nextStatus,
        memberReason: memberReason.trim() || null,
        internalNote: internalNote.trim() || null,
        result: nextStatus === 'COMPLETED'
          ? {
              actionType: actionType as AdminActionType,
              targetType: resultTargetType as AdminTargetType,
              targetId: resultTargetId.trim(),
            }
          : null,
      })
      setSelected(updated)
      resetReviewForm(updated)
      await loadList()
      setNotice(nextStatus === 'ACCEPTED'
        ? '승인했습니다. 실제 데이터 반영을 완료한 뒤 처리 완료로 전환해 주세요.'
        : '상태를 저장하고 최신 목록을 불러왔습니다.')
    } catch (reason) {
      const apiError = reason instanceof AdminApiError ? reason : null
      const synchronized = await refreshAfterTransitionConflict(
        apiError?.code,
        loadList,
        async () => {
          const detail = await getAdminParticipationDetail(kind, selected.requestId)
          setSelected(detail)
          resetReviewForm(detail)
        },
      ).catch(() => false)
      setError(true)
      setNotice(synchronized
        ? `${messageFor(reason)} 목록과 상세를 최신 서버 상태로 다시 불러왔습니다.`
        : messageFor(reason))
    } finally {
      setBusy(false)
    }
  }

  const nextStatuses = selected ? allowedNextStatuses(selected.status) : []
  const completing = selected?.status === 'ACCEPTED'

  return <div className={styles.screen}>
    <p className={styles.intro}>오래 접수된 요청부터 검토합니다. 승인은 실제 데이터 반영과 별개입니다.</p>

    <div className={styles.tabs} role="tablist" aria-label="검토 요청 종류">
      <button type="button" role="tab" disabled={busy} aria-selected={kind === 'submission'} onClick={() => changeQuery({ kind: 'submission' })}>제보</button>
      <button type="button" role="tab" disabled={busy} aria-selected={kind === 'report'} onClick={() => changeQuery({ kind: 'report' })}>신고</button>
    </div>

    <section className={styles.queue} aria-label="검토 대기열">
      <div className={styles.filters}>
        <label>상태
          <select disabled={busy} value={status} onChange={(event) => changeQuery({ status: event.target.value as AdminParticipationStatus | '' })}>
            <option value="">전체</option>
            {STATUSES.map((value) => <option key={value} value={value}>{STATUS_LABELS[value]}</option>)}
          </select>
        </label>
        <label>대상 유형
          <select disabled={busy} value={targetType} onChange={(event) => changeQuery({ targetType: event.target.value as AdminTargetType | '' })}>
            <option value="">전체</option>
            {TARGET_TYPES.map((value) => <option key={value} value={value}>{TARGET_TYPE_LABELS[value]}</option>)}
          </select>
        </label>
        <Button variant="secondary" disabled={busy} onClick={() => void refreshList()}>새로고침</Button>
      </div>

      <ul className={styles.list}>
        {items.map((item) => <li key={item.requestId}>
          <button type="button" disabled={busy} className={styles.item} aria-current={selected?.requestId === item.requestId} onClick={() => void openDetail(item)}>
            <span className={styles.itemHeader}><strong>{TARGET_TYPE_LABELS[item.targetType]}</strong><StatusBadge className={styles.itemStatus} tone={participationStatusTone(item.status)}>{STATUS_LABELS[item.status]}</StatusBadge></span>
            <span>{item.description}</span>
            <small>{new Date(item.createdAt).toLocaleString('ko-KR')} · 회원 {item.memberId ?? '연결 제거됨'}</small>
          </button>
        </li>)}
      </ul>

      <nav className={styles.pagination} aria-label="관리자 검토 목록 페이지">
        <Button variant="secondary" disabled={busy || pageNumber <= 1} onClick={() => setPageNumber(pageNumber - 1)}>이전</Button>
        <span>{pageNumber} / {Math.max(totalPages, 1)} 페이지</span>
        <Button variant="secondary" disabled={busy || !hasNext} onClick={() => setPageNumber(pageNumber + 1)}>다음</Button>
      </nav>
    </section>

    {selected ? <section className={styles.detail} aria-label="요청 상세">
      <h2>요청 상세</h2>
      <dl className={styles.metadata}>
        <div><dt>요청 ID</dt><dd>{selected.requestId}</dd></div>
        <div><dt>회원 ID</dt><dd>{selected.memberId ?? '보존 정책에 따라 식별 연결이 제거됨'}</dd></div>
        <div><dt>상태</dt><dd>{STATUS_LABELS[selected.status]}</dd></div>
        <div><dt>대상</dt><dd>{TARGET_TYPE_LABELS[selected.targetType]}</dd></div>
        {targetDetails(selected).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
        <div><dt>회원 요청 원문</dt><dd className={styles.originalText}>{selected.description}</dd></div>
        <div><dt>현재 공개 사유</dt><dd>{selected.memberReason || '없음'}</dd></div>
        <div><dt>현재 내부 메모</dt><dd>{selected.internalNote || '없음'}</dd></div>
        <div><dt>현재 처리 결과</dt><dd>{resultSummary(selected.result)}</dd></div>
      </dl>
      {selected.evidenceUrl ? <p className={styles.externalLink}>
        외부 사이트로 이동합니다. <a href={selected.evidenceUrl} target="_blank" rel="noopener noreferrer">근거 URL을 새 탭에서 열기</a>
      </p> : <p className={styles.muted}>제출된 근거 URL이 없습니다.</p>}
      {selected.status === 'ACCEPTED' ? <p className={styles.acceptedNotice}>승인된 요청입니다. 실제 데이터 반영을 마친 뒤에만 처리 완료로 전환하세요.</p> : null}

      {nextStatuses.length ? <div className={styles.reviewForm}>
        <h3>검토 상태 변경</h3>
        <label>회원 공개 사유 <span>반려·처리 완료 필수, 1~1000자</span>
          <textarea rows={4} maxLength={1000} value={memberReason} onChange={(event) => setMemberReason(event.target.value)} />
        </label>
        <label>관리자 내부 메모
          <textarea rows={4} value={internalNote} onChange={(event) => setInternalNote(event.target.value)} />
        </label>
        {completing ? <fieldset className={styles.completion}>
          <legend>실제 조치 결과</legend>
          <label className={styles.checkbox}><input type="checkbox" checked={actionConfirmed} onChange={(event) => setActionConfirmed(event.target.checked)} /> 실제 데이터 등록·정정·숨김 조치를 완료했습니다.</label>
          <label>조치 유형<select value={actionType} onChange={(event) => setActionType(event.target.value as AdminActionType | '')}>
            <option value="">선택</option>{ACTION_TYPES.map((value) => <option key={value} value={value}>{ACTION_TYPE_LABELS[value]}</option>)}
          </select></label>
          <label>조치 대상 유형<select value={resultTargetType} onChange={(event) => setResultTargetType(event.target.value as AdminTargetType | '')}>
            <option value="">선택</option>{TARGET_TYPES.map((value) => <option key={value} value={value}>{TARGET_TYPE_LABELS[value]}</option>)}
          </select></label>
          <label>조치 대상 ID<input value={resultTargetId} onChange={(event) => setResultTargetId(event.target.value)} /></label>
        </fieldset> : null}
        <div className={styles.actions}>
          {nextStatuses.map((next) => <Button key={next} disabled={busy} variant={next === 'REJECTED' ? 'secondary' : 'primary'} onClick={() => void changeStatus(next)}>
            {STATUS_LABELS[next]}로 변경
          </Button>)}
        </div>
      </div> : <p className={styles.muted}>종료된 요청이므로 변경 가능한 다음 상태가 없습니다.</p>}

      <section className={styles.history}>
        <h3>감사 이력</h3>
        {selected.moderationHistory?.length ? <ol>
          {selected.moderationHistory.map((entry) => <li key={entry.historyId}>
            <strong>{STATUS_LABELS[entry.fromStatus]} → {STATUS_LABELS[entry.toStatus]}</strong>
            <span>{new Date(entry.createdAt).toLocaleString('ko-KR')} · 관리자 {entry.adminId}</span>
            <span>공개 사유: {entry.memberReason || '없음'}</span>
            <span>내부 메모: {entry.internalNote || '없음'}</span>
            <span>처리 결과: {resultSummary(entry.result)}</span>
          </li>)}
        </ol> : <p className={styles.muted}>아직 기록된 상태 변경이 없습니다.</p>}
      </section>
    </section> : <p className={styles.muted}>목록에서 요청을 선택하면 원문과 검토 이력을 확인할 수 있습니다.</p>}

    {notice ? <p className={error ? styles.error : styles.notice} role={error ? 'alert' : 'status'}>{notice}</p> : null}
  </div>
}
