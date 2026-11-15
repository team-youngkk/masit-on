'use client'

import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { StatePanel } from '@/components/ui/StatePanel'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { aiConfidenceTone } from '@/lib/admin/ai-confidence'
import { aiDiagnosticAction, aiDiagnosticLabel } from '@/lib/admin/ai-diagnostics'
import {
  aiExtractionMessageFor,
  discardAllBlockedRegistrationUnits,
  getAiVideoExtraction,
  retryAiVideoExtraction,
  type AiCandidate,
  type AiExtractionDetail,
} from '@/lib/admin/ai-video-extractions'
import { candidateTruncatedBannerMessage, extractionResultNotice, registrationSummaryLabel, registrationUnitSummary, retryActionAvailable } from '@/lib/admin/ai-video-extractions-coordination'

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
  const [discardAllOpen, setDiscardAllOpen] = useState(false)
  const [discardAllReason, setDiscardAllReason] = useState('')
  const requestId = useRef(0)

  const load = useCallback(async () => {
    const currentRequest = ++requestId.current
    const next = await getAiVideoExtraction(jobId)
    if (currentRequest !== requestId.current) return { requestId: currentRequest, applied: false }
    setData(next)
    return { requestId: currentRequest, applied: true }
  }, [jobId])

  const refresh = useCallback(async (message = '') => {
    setBusy(true); setError(false)
    const expectedRequest = requestId.current + 1
    try {
      const result = await load()
      if (result.applied && requestId.current === result.requestId) setNotice(message)
    }
    catch (reason) {
      if (requestId.current === expectedRequest) { setError(true); setNotice(aiExtractionMessageFor(reason)) }
    }
    finally {
      if (requestId.current === expectedRequest) setBusy(false)
    }
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

  async function discardAll() {
    setBusy(true); setError(false)
    try {
      const result = await discardAllBlockedRegistrationUnits(jobId, discardAllReason)
      setDiscardAllOpen(false); setDiscardAllReason('')
      await refresh(`${result.discardedCount}건 폐기했습니다.`)
    } catch (caught) { setError(true); setNotice(aiExtractionMessageFor(caught)) }
    finally { setBusy(false) }
  }

  if (busy && !data) return <StatePanel title="AI 작업 상세를 불러오는 중입니다" />
  if (!data) return <StatePanel tone="danger" title="AI 작업 상세를 불러오지 못했습니다" description={notice || '잠시 후 다시 시도해 주세요.'} actions={<Button variant="secondary" disabled={busy} onClick={() => void refresh()}>다시 시도</Button>} />

  const retryAvailable = retryActionAvailable(data, data.registrationUnits)
  const truncatedMessage = candidateTruncatedBannerMessage(data.candidateTruncated)
  const hasBlockedUnit = data.registrationUnits.some((unit) => unit.reviewStatus === 'AUTO_BLOCKED')
  const summary = registrationUnitSummary(data.registrationUnits)
  const resultNotice = extractionResultNotice(data.executionStatus, data.candidates.length, data.registrationUnits.length)

  return <div className={styles.detail}>
    <div className={styles.toolbar}><Link href="/admin/ai">← 작업 목록</Link><Button variant="secondary" disabled={busy} onClick={() => void refresh('최신 상태를 조회했습니다.')}>새로고침</Button></div>

    {truncatedMessage ? <StatePanel compact tone="warning" className={styles.banner} title={truncatedMessage} /> : null}

    <section className={styles.panel} aria-labelledby="job-summary-heading">
      <div className={styles.toolbar}>
        <div>
          <p className={styles.sectionKicker}>AI 운영</p>
          <h2 id="job-summary-heading">작업 상태</h2>
        </div>
        <p className={styles.meta}>작업 ID <code>{data.jobId}</code></p>
      </div>
      {resultNotice ? <p className={styles.notice}>{resultNotice}</p> : null}
      <div className={styles.statusSummary} aria-label="AI 작업 상태 요약">
        <div className={styles.statusItem}>
          <span className={styles.statusItemLabel}>실행 상태</span>
          <div className={styles.statusItemValue}><StatusBadge tone={executionTone(data.executionStatus)}>{executionLabel(data.executionStatus)}</StatusBadge><span className={styles.code}>{data.executionStatus}</span></div>
        </div>
        <div className={styles.statusItem}>
          <span className={styles.statusItemLabel}>결과 완전성</span>
          <div className={styles.statusItemValue}><StatusBadge tone={completenessTone(data.resultCompleteness)}>{completenessLabel(data.resultCompleteness)}</StatusBadge>{data.resultCompleteness ? <span className={styles.code}>{data.resultCompleteness}</span> : null}</div>
        </div>
        <div className={styles.statusItem}>
          <span className={styles.statusItemLabel}>자동 등록 상태</span>
          <div className={styles.statusItemValue}><StatusBadge tone={reviewTone(data.reviewStatus)}>{reviewLabel(data.reviewStatus)}</StatusBadge>{data.reviewStatus ? <span className={styles.code}>{data.reviewStatus}</span> : null}</div>
        </div>
      </div>
      <dl className={styles.registrationSummary} aria-label="등록 결과 집계">
        <div className={styles.summaryItem}><dt>등록 결과</dt><dd>{registrationSummaryLabel(summary)}</dd></div>
        <div className={styles.summaryItem}><dt>현재 등록</dt><dd>{summary.registered}/{summary.total}개</dd></div>
        <div className={styles.summaryItem}><dt>자동 확정</dt><dd>{summary.autoConfirmed}개</dd></div>
        <div className={styles.summaryItem}><dt>자동 보류 / 거부</dt><dd>{summary.autoBlocked}개 / {summary.autoRejected}개</dd></div>
        <div className={styles.summaryItem}><dt>수동 보정</dt><dd>{summary.manualOverride}개</dd></div>
      </dl>
      <dl className={styles.metaGrid}>
        <div className={styles.metaItem}><dt>유입 경로</dt><dd>{sourceLabel(data.source)}</dd></div>
        <div className={styles.metaItem}><dt>시도</dt><dd>{data.attemptCount}회</dd></div>
        <div className={styles.metaItem}><dt>버전</dt><dd>{data.provider} / {data.modelVersion} / {data.promptVersion} / {data.schemaVersion}</dd></div>
        <div className={styles.metaItem}><dt>생성 / 시작 / 완료</dt><dd>{formatDate(data.createdAt)} / {formatDate(data.startedAt)} / {formatDate(data.finishedAt)}</dd></div>
      </dl>
      <p className={styles.meta}><a className={styles.videoLink} href={data.youtube.videoUrl} target="_blank" rel="noreferrer">원본 유튜브 영상 보기</a></p>
      {data.error ? <p className={styles.warning} role="alert"><strong>실행 실패</strong><span>{aiDiagnosticLabel(data.failureDiagnostic, data.error.category)}</span><span>시도 {data.attemptCount}회</span>{aiDiagnosticAction(data.failureDiagnostic) ? <span>{aiDiagnosticAction(data.failureDiagnostic)}</span> : null}</p> : null}
    </section>

    <section className={styles.panel} aria-labelledby="registration-unit-heading">
      <div className={styles.toolbar}>
        <h2 id="registration-unit-heading">등록 단위</h2>
        {hasBlockedUnit ? <Button variant="secondary" disabled={busy} onClick={() => setDiscardAllOpen((open) => !open)}>AUTO_BLOCKED 전체 폐기</Button> : null}
      </div>
      <p className={styles.hint}>장소 동일성과 대표 음식 카테고리는 관리자 입력 없이 시스템이 판정합니다. 예외만 아래에서 보충하거나 폐기할 수 있습니다.</p>
      {discardAllOpen ? <form className={styles.retryForm} onSubmit={(event) => { event.preventDefault(); void discardAll() }}>
        <label>폐기 사유<input required value={discardAllReason} onChange={(event) => setDiscardAllReason(event.target.value)} /></label>
        <div className={styles.actions}><Button type="submit" disabled={busy}>전체 폐기 제출</Button><Button variant="secondary" disabled={busy} onClick={() => setDiscardAllOpen(false)}>취소</Button></div>
      </form> : null}
      <AiRegistrationUnits jobId={jobId} units={data.registrationUnits} refresh={refresh} onRequestRetry={() => setRetryOpen(true)} />
    </section>

    <section className={styles.panel} aria-labelledby="candidate-heading">
      <h2 id="candidate-heading">후보 Snapshot</h2>
      <p className={styles.hint}>입력 원문, 보완 텍스트, Provider 응답 전문과 비밀정보는 표시하지 않습니다.</p>
      {data.candidates.length ? <ul className={styles.candidates}>{data.candidates.map((candidate, index) => <CandidateCard key={candidate.candidateTagId ?? `${candidate.field}-${index}`} candidate={candidate} />)}</ul> : <p className={styles.hint}>표시할 후보가 없습니다.</p>}
      {data.missingFields.length ? <p className={styles.warning}>누락 필드: {data.missingFields.join(', ')}</p> : null}
      {data.snapshotDiagnostic ? <p className={styles.warning}>후보 판정 사유: {aiDiagnosticLabel(data.snapshotDiagnostic)}</p> : null}
    </section>

    <section className={styles.panel} aria-labelledby="attempt-heading">
      <h2 id="attempt-heading">실행 시도</h2>
      {data.attempts.length ? <ul className={styles.attemptList}>{data.attempts.map((attempt) => <li className={styles.attemptItem} key={attempt.attemptNo}><span>시도 #{attempt.attemptNo}</span><span>{attempt.outcome}</span>{attempt.errorCategory ? <span>{aiDiagnosticLabel(attempt.failureDiagnostic, attempt.errorCategory)}</span> : null}</li>)}</ul> : <p className={styles.hint}>기록된 실행 시도가 없습니다.</p>}
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
  const confidence = confidencePresentation(candidate.confidence)
  return <li className={styles.candidate}>
    <div className={styles.cardHeading}>
      <p className={styles.sectionKicker}>후보 필드</p>
      <h3>{candidate.field}</h3>
      <p className={styles.candidateValue}>{candidate.label ?? candidate.value ?? candidate.normalizedCode ?? '값 없음'}</p>
    </div>
    <div className={styles.candidateConfidence} aria-label={`신뢰도 ${confidence.label} ${confidence.percentage}%`}>
      <span>신뢰도</span>
      <StatusBadge tone={aiConfidenceTone(candidate.confidence)}>{confidence.label}</StatusBadge>
      <span>{confidence.percentage}% ({candidate.confidence.toFixed(2)})</span>
    </div>
    <dl className={styles.candidateEvidence}>
      <dt>근거</dt>
      <dd>{evidenceLabel(candidate)}</dd>
    </dl>
  </li>
}

function executionTone(status: AiExtractionDetail['executionStatus']): 'success' | 'warning' | 'danger' | 'neutral' {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'neutral'
}

function executionLabel(status: AiExtractionDetail['executionStatus']): string {
  if (status === 'QUEUED') return '대기'
  if (status === 'RUNNING') return '실행 중'
  if (status === 'SUCCEEDED') return '성공'
  return '실패'
}

function completenessTone(status: AiExtractionDetail['resultCompleteness']): 'success' | 'warning' | 'neutral' {
  if (status === 'COMPLETE') return 'success'
  if (status === 'PARTIAL') return 'warning'
  return 'neutral'
}

function completenessLabel(status: AiExtractionDetail['resultCompleteness']): string {
  if (status === 'COMPLETE') return '완전'
  if (status === 'PARTIAL') return '부분'
  return '미완료'
}

function reviewLabel(status: AiExtractionDetail['reviewStatus']): string {
  if (status === 'AUTO_CONFIRMED') return '자동 확정·등록 유지'
  if (status === 'MANUAL_OVERRIDE') return '수동 보정·등록 유지'
  if (status === 'AUTO_BLOCKED') return '자동 보류'
  if (status === 'AUTO_REJECTED') return '자동 거부'
  return '미정'
}

function sourceLabel(source: AiExtractionDetail['source']): string {
  return source === 'WEBHOOK' ? 'Webhook' : '관리자'
}

function confidencePresentation(confidence: number): { label: string; percentage: number } {
  if (!Number.isFinite(confidence)) return { label: '확인 필요', percentage: 0 }
  if (confidence < 0.6) return { label: '낮음', percentage: Math.round(confidence * 100) }
  if (confidence < 0.8) return { label: '중간', percentage: Math.round(confidence * 100) }
  return { label: '높음', percentage: Math.round(confidence * 100) }
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

function formatDate(value: string | null): string {
  if (!value) return '기록 없음'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? '기록 없음' : parsed.toLocaleString('ko-KR')
}
