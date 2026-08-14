'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { aiExtractionMessageFor, getAiVideoExtraction, retryAiVideoExtraction, reviewAiVideoExtraction, type AiCandidate, type AiExtractionDetail, type AiTagDecision } from '@/lib/admin/ai-video-extractions'
import { reviewActionsFor, reviewRequest } from '@/lib/admin/ai-video-extractions-coordination'

import { AiCandidateRegistration } from './AiCandidateRegistration'
import styles from './AiVideoExtractionScreen.module.css'

export function AiVideoExtractionDetail({ jobId }: { jobId: string }) {
  const [data, setData] = useState<AiExtractionDetail | null>(null)
  const [busy, setBusy] = useState(true)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState(false)
  const [retryOpen, setRetryOpen] = useState(false)
  const [supplementText, setSupplementText] = useState('')
  const [reason, setReason] = useState('')
  const [tagCodes, setTagCodes] = useState<Record<string, string>>({})
  const [selectedCandidate, setSelectedCandidate] = useState<AiCandidate | null>(null)

  const load = useCallback(async () => {
    const next = await getAiVideoExtraction(jobId)
    setData(next)
    return next
  }, [jobId])

  const refresh = useCallback(async (message = '') => {
    setBusy(true); setError(false)
    try { await load(); setNotice(message) }
    catch (reason) { setError(true); setNotice(aiExtractionMessageFor(reason)) }
    finally { setBusy(false) }
  }, [load])

  useEffect(() => { void refresh() }, [refresh])

  async function retry() {
    setBusy(true); setError(false)
    try {
      const next = await retryAiVideoExtraction(jobId, supplementText, reason)
      setRetryOpen(false); setSupplementText(''); setReason('')
      setNotice(`새 작업 ${next.jobId}을 요청했습니다.`)
    } catch (reason) { setError(true); setNotice(aiExtractionMessageFor(reason)) }
    finally { setBusy(false) }
  }

  async function review(decision: 'CONFIRM' | 'DISCARD' | 'ROLLBACK') {
    if (!data?.reviewStatus) return
    setBusy(true); setError(false)
    try {
      const request = reviewRequest(decision, data.reviewStatus)
      const tagDecisions: AiTagDecision[] = data.candidates.flatMap((candidate) => {
        if (!candidate.candidateTagId) return []
        const original = candidate.normalizedCode ?? ''
        const next = (tagCodes[candidate.candidateTagId] ?? original).trim()
        return next && next !== original ? [{ candidateTagId: candidate.candidateTagId, decision: 'MANUAL_OVERRIDE' as const, tagCode: next }] : []
      })
      await reviewAiVideoExtraction(jobId, request.decision, request.expectedReviewStatus, reason, tagDecisions)
      setReason(''); await load(); setNotice('검수 상태를 갱신했습니다.')
    } catch (reason) {
      setError(true); setNotice(aiExtractionMessageFor(reason))
      if (reason instanceof Error && 'status' in reason && reason.status === 409) await refresh('다른 변경과 충돌했습니다. 최신 상태를 다시 조회했습니다. 검토 후 다시 진행해 주세요.')
    } finally { setBusy(false) }
  }

  if (busy && !data) return <p role="status">AI 작업 상세를 불러오는 중입니다.</p>
  if (!data) return <div className={styles.detail}><p className={styles.error} role="alert">{notice || 'AI 작업 상세를 불러오지 못했습니다.'}</p><Button variant="secondary" disabled={busy} onClick={() => void refresh()}>다시 시도</Button></div>

  const actions = reviewActionsFor(data)
  return <div className={styles.detail}>
    <div className={styles.toolbar}><Link href="/admin/ai">← 작업 목록</Link><Button variant="secondary" disabled={busy} onClick={() => void refresh('최신 상태를 조회했습니다.')}>새로고침</Button></div>
    <section className={styles.panel} aria-labelledby="job-summary-heading">
      <h2 id="job-summary-heading">작업 상태</h2>
      <p className={styles.meta}><code>{data.jobId}</code></p>
      <p>실행 {data.executionStatus} · 결과 {data.resultCompleteness ?? '미완료'} · 검수 {data.reviewStatus ?? '미정'} · 시도 {data.attemptCount}회</p>
      <p className={styles.meta}>버전 {data.provider} / {data.modelVersion} / {data.promptVersion} / {data.schemaVersion}</p>
      {data.error ? <p className={styles.warning} role="alert">실패 범주: {data.error.category} · {data.error.retryable ? '재시도 가능' : '재시도 불가'} · 시도 {data.attemptCount}회</p> : null}
      {data.reviewStatus === 'AUTO_CONFIRMED' ? <p className={styles.notice}>자동 확정은 사전 승인 대상이 아닙니다. 필요 시 롤백만 할 수 있습니다.</p> : null}
    </section>

    <section className={styles.panel} aria-labelledby="candidate-heading">
      <h2 id="candidate-heading">후보 Snapshot</h2>
      <p className={styles.hint}>입력 원문, 보완 텍스트, Provider 응답 전문과 비밀정보는 표시하지 않습니다.</p>
      {data.candidates.length ? <ul className={styles.candidates}>{data.candidates.map((candidate, index) => <CandidateCard key={candidate.candidateTagId ?? `${candidate.field}-${index}`} candidate={candidate} tagCode={candidate.candidateTagId ? (tagCodes[candidate.candidateTagId] ?? candidate.normalizedCode ?? '') : undefined} onTagCodeChange={candidate.candidateTagId ? (value) => setTagCodes((current) => ({ ...current, [candidate.candidateTagId!]: value })) : undefined} onSelectForRegistration={!selectedCandidate && candidate.field === 'restaurantName' ? () => setSelectedCandidate(candidate) : undefined} />)}</ul> : <p className={styles.hint}>표시할 후보가 없습니다.</p>}
      {data.missingFields.length ? <p className={styles.warning}>누락 필드: {data.missingFields.join(', ')}</p> : null}
    </section>

    {selectedCandidate ? (
      <AiCandidateRegistration detail={data} candidate={selectedCandidate} onCancel={() => setSelectedCandidate(null)} />
    ) : null}

    <section className={styles.panel} aria-labelledby="attempt-heading">
      <h2 id="attempt-heading">실행 시도</h2>
      {data.attempts.length ? <ul>{data.attempts.map((attempt) => <li key={attempt.attemptNo}>#{attempt.attemptNo} · {attempt.outcome}{attempt.errorCategory ? ` · ${attempt.errorCategory}` : ''}</li>)}</ul> : <p className={styles.hint}>기록된 실행 시도가 없습니다.</p>}
    </section>

    <section className={styles.panel} aria-labelledby="actions-heading">
      <h2 id="actions-heading">상태별 조치</h2>
      {actions.retry ? <Button variant="secondary" disabled={busy} onClick={() => setRetryOpen((open) => !open)}>재시도</Button> : null}
      {retryOpen ? <form className={styles.retryForm} onSubmit={(event) => { event.preventDefault(); void retry() }}>
        <label>새 보완 텍스트<textarea required value={supplementText} maxLength={20000} rows={4} onChange={(event) => setSupplementText(event.target.value)} /></label>
        <label>재시도 사유<input required value={reason} onChange={(event) => setReason(event.target.value)} /></label>
        <div className={styles.actions}><Button type="submit" disabled={busy}>새 작업 요청</Button><Button variant="secondary" disabled={busy} onClick={() => setRetryOpen(false)}>취소</Button></div>
      </form> : null}
      {(actions.confirm || actions.discard || actions.rollback) ? <>
        <label className={styles.retryForm}>검수 사유<input required value={reason} onChange={(event) => setReason(event.target.value)} /></label>
        <div className={styles.actions}>
          {actions.confirm ? <Button disabled={busy} onClick={() => void review('CONFIRM')}>CONFIRM</Button> : null}
          {actions.discard ? <Button variant="secondary" disabled={busy} onClick={() => void review('DISCARD')}>DISCARD</Button> : null}
          {actions.rollback ? <Button variant="secondary" disabled={busy} onClick={() => void review('ROLLBACK')}>ROLLBACK</Button> : null}
        </div>
      </> : <p className={styles.hint}>현재 상태에서 가능한 수동 조치가 없습니다.</p>}
    </section>
    {notice ? <p className={error ? styles.error : styles.notice} role={error ? 'alert' : 'status'}>{notice}</p> : null}
  </div>
}

function CandidateCard({ candidate, tagCode, onTagCodeChange, onSelectForRegistration }: { candidate: AiCandidate; tagCode?: string; onTagCodeChange?: (value: string) => void; onSelectForRegistration?: () => void }) {
  return <li className={styles.candidate}>
    <h3>{candidate.field}</h3>
    <p>{candidate.label ?? candidate.value ?? candidate.normalizedCode ?? '값 없음'}</p>
    {onSelectForRegistration ? <Button variant="secondary" onClick={onSelectForRegistration}>이 후보로 등록 시작</Button> : null}
    {onTagCodeChange ? <label>태그 코드 보정<input value={tagCode ?? ''} maxLength={64} onChange={(event) => onTagCodeChange(event.target.value)} /></label> : null}
    <p className={styles.meta}>신뢰도 {(candidate.confidence * 100).toFixed(0)}% · 근거 {evidenceLabel(candidate)}</p>
  </li>
}

function evidenceLabel(candidate: AiCandidate) {
  const { evidence } = candidate
  if (evidence.type === 'TIMESTAMP') return `TIMESTAMP ${formatTimestamp(evidence.startMs)}–${formatTimestamp(evidence.endMs)}`
  if (evidence.type === 'TEXT_RANGE') return `TEXT_RANGE ${evidence.startOffset}–${evidence.endOffset}`
  return 'UNKNOWN'
}

function formatTimestamp(milliseconds: number) {
  const seconds = Math.floor(milliseconds / 1000)
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}
