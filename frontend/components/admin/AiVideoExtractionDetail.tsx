'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { StatePanel } from '@/components/ui/StatePanel'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { aiConfidenceTone } from '@/lib/admin/ai-confidence'
import { aiExtractionMessageFor, getAiVideoExtraction, retryAiVideoExtraction, type AiCandidate, type AiExtractionDetail } from '@/lib/admin/ai-video-extractions'
import { candidateTruncatedBannerMessage, retryActionAvailable } from '@/lib/admin/ai-video-extractions-coordination'

import { AiRegistrationUnits } from './AiRegistrationUnits'
import styles from './AiVideoExtractionScreen.module.css'

export function AiVideoExtractionDetail({ jobId }: { jobId: string }) {
  const [data, setData] = useState<AiExtractionDetail | null>(null)
  const [busy, setBusy] = useState(true)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState(false)
  const [retryOpen, setRetryOpen] = useState(false)
  const [supplementText, setSupplementText] = useState('')
  const [reason, setReason] = useState('')

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

  if (busy && !data) return <StatePanel title="AI 작업 상세를 불러오는 중입니다" />
  if (!data) return <StatePanel tone="danger" title="AI 작업 상세를 불러오지 못했습니다" description={notice || '잠시 후 다시 시도해 주세요.'} actions={<Button variant="secondary" disabled={busy} onClick={() => void refresh()}>다시 시도</Button>} />

  const retryAvailable = retryActionAvailable(data)
  const truncatedMessage = candidateTruncatedBannerMessage(data.candidateTruncated)

  return <div className={styles.detail}>
    <div className={styles.toolbar}><Link href="/admin/ai">← 작업 목록</Link><Button variant="secondary" disabled={busy} onClick={() => void refresh('최신 상태를 조회했습니다.')}>새로고침</Button></div>

    {truncatedMessage ? <StatePanel compact tone="warning" className={styles.banner} title={truncatedMessage} /> : null}

    <section className={styles.panel} aria-labelledby="job-summary-heading">
      <h2 id="job-summary-heading">작업 상태</h2>
      <p className={styles.meta}><code>{data.jobId}</code></p>
      <div className={styles.meta}><StatusBadge tone={executionTone(data.executionStatus)}>{data.executionStatus}</StatusBadge><StatusBadge>{data.resultCompleteness ?? '미완료'}</StatusBadge><StatusBadge tone={reviewTone(data.reviewStatus)}>{data.reviewStatus ?? '미정'}</StatusBadge><span>시도 {data.attemptCount}회</span></div>
      <p className={styles.meta}>버전 {data.provider} / {data.modelVersion} / {data.promptVersion} / {data.schemaVersion}</p>
      {data.error ? <p className={styles.warning} role="alert">실패 범주: {data.error.category} · {data.error.retryable ? '재시도 가능' : '재시도 불가'} · 시도 {data.attemptCount}회</p> : null}
    </section>

    <section className={styles.panel} aria-labelledby="registration-unit-heading">
      <h2 id="registration-unit-heading">등록 단위</h2>
      <p className={styles.hint}>장소 동일성과 대표 음식 카테고리는 관리자 입력 없이 시스템이 판정합니다. 예외만 아래에서 보충하거나 폐기할 수 있습니다.</p>
      <AiRegistrationUnits jobId={jobId} units={data.registrationUnits} refresh={refresh} onRequestRetry={() => setRetryOpen(true)} />
    </section>

    <section className={styles.panel} aria-labelledby="candidate-heading">
      <h2 id="candidate-heading">후보 Snapshot</h2>
      <p className={styles.hint}>입력 원문, 보완 텍스트, Provider 응답 전문과 비밀정보는 표시하지 않습니다.</p>
      {data.candidates.length ? <ul className={styles.candidates}>{data.candidates.map((candidate, index) => <CandidateCard key={candidate.candidateTagId ?? `${candidate.field}-${index}`} candidate={candidate} />)}</ul> : <p className={styles.hint}>표시할 후보가 없습니다.</p>}
      {data.missingFields.length ? <p className={styles.warning}>누락 필드: {data.missingFields.join(', ')}</p> : null}
    </section>

    <section className={styles.panel} aria-labelledby="attempt-heading">
      <h2 id="attempt-heading">실행 시도</h2>
      {data.attempts.length ? <ul>{data.attempts.map((attempt) => <li key={attempt.attemptNo}>#{attempt.attemptNo} · {attempt.outcome}{attempt.errorCategory ? ` · ${attempt.errorCategory}` : ''}</li>)}</ul> : <p className={styles.hint}>기록된 실행 시도가 없습니다.</p>}
    </section>

    <section className={styles.panel} aria-labelledby="retry-heading">
      <h2 id="retry-heading">재시도</h2>
      {retryAvailable ? <Button variant="secondary" disabled={busy} onClick={() => setRetryOpen((open) => !open)}>재시도</Button> : <p className={styles.hint}>현재 작업 상태에서는 재시도할 수 없습니다.</p>}
      {retryOpen ? <form className={styles.retryForm} onSubmit={(event) => { event.preventDefault(); void retry() }}>
        <label>새 보완 텍스트<textarea required value={supplementText} maxLength={20000} rows={4} onChange={(event) => setSupplementText(event.target.value)} /></label>
        <label>재시도 사유<input required value={reason} onChange={(event) => setReason(event.target.value)} /></label>
        <div className={styles.actions}><Button type="submit" disabled={busy}>새 작업 요청</Button><Button variant="secondary" disabled={busy} onClick={() => setRetryOpen(false)}>취소</Button></div>
      </form> : null}
    </section>
    {notice ? <p className={error ? styles.error : styles.notice} role={error ? 'alert' : 'status'}>{notice}</p> : null}
  </div>
}

function CandidateCard({ candidate }: { candidate: AiCandidate }) {
  return <li className={styles.candidate}>
    <h3>{candidate.field}</h3>
    <p>{candidate.label ?? candidate.value ?? candidate.normalizedCode ?? '값 없음'}</p>
    <p className={styles.meta}><StatusBadge tone={aiConfidenceTone(candidate.confidence)}>신뢰도 {(candidate.confidence * 100).toFixed(0)}%</StatusBadge> 근거 {evidenceLabel(candidate)}</p>
  </li>
}

function executionTone(status: AiExtractionDetail['executionStatus']): 'success' | 'warning' | 'danger' | 'neutral' {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'neutral'
}

function reviewTone(status: AiExtractionDetail['reviewStatus']): 'success' | 'warning' | 'danger' | 'neutral' {
  if (status === 'AUTO_CONFIRMED' || status === 'MANUAL_OVERRIDE') return 'success'
  if (status === 'AUTO_BLOCKED') return 'warning'
  if (status === 'AUTO_REJECTED') return 'danger'
  return 'neutral'
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
