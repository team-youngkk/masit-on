'use client'

import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { StatePanel } from '@/components/ui/StatePanel'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { AdminApiError } from '@/lib/admin/api'
import { createAiVideoExtraction, getAiVideoExtractions, aiExtractionMessageFor, type AiExtractionJob, type AiExtractionPage, type AiExecutionStatus, type AiExtractionReviewStatus, type AiExtractionSource, type AiExtractionSubmissionResult } from '@/lib/admin/ai-video-extractions'
import { aiDiagnosticAction, aiDiagnosticLabel } from '@/lib/admin/ai-diagnostics'
import {
  aiCompletenessLabel,
  aiCompletenessTone,
  aiExecutionLabel,
  aiExecutionTone,
  aiExtractionSubmissionAttempt,
  aiExtractionSubmissionFieldErrors,
  aiExtractionSubmissionPresentation,
  aiReviewLabel,
  aiReviewTone,
  aiSourceLabel,
  nextAiExtractionFilters,
  type AiExtractionFilters,
  type AiExtractionSubmissionAttempt,
  type AiExtractionSubmissionFieldErrors,
} from '@/lib/admin/ai-video-extractions-coordination'

import styles from './AiVideoExtractionScreen.module.css'

const initialFilters: AiExtractionFilters = { executionStatus: '', source: '', reviewStatus: '', page: 1 }

export function AiVideoExtractionList() {
  const [filters, setFilters] = useState(initialFilters)
  const [data, setData] = useState<AiExtractionPage | null>(null)
  const [busy, setBusy] = useState(true)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState(false)
  const [videoUrl, setVideoUrl] = useState('')
  const [supplementText, setSupplementText] = useState('')
  const [submitBusy, setSubmitBusy] = useState(false)
  const [submitNotice, setSubmitNotice] = useState('')
  const [submitError, setSubmitError] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<AiExtractionSubmissionFieldErrors>({})
  const [submittedResult, setSubmittedResult] = useState<AiExtractionSubmissionResult | null>(null)
  const videoUrlInput = useRef<HTMLInputElement>(null)
  const supplementTextInput = useRef<HTMLTextAreaElement>(null)
  const submissionAttempt = useRef<AiExtractionSubmissionAttempt | null>(null)
  const submitInFlight = useRef(false)
  const requestId = useRef(0)
  const [refreshVersion, setRefreshVersion] = useState(0)

  const load = useCallback(async () => {
    const currentRequest = ++requestId.current
    const next = await getAiVideoExtractions(filters)
    if (currentRequest === requestId.current) setData(next)
  }, [filters])

  useEffect(() => {
    let active = true
    setBusy(true)
    setData(null)
    setError(false)
    setNotice('')
    void load().then(() => { if (active) { setError(false); setNotice('') } })
      .catch((reason) => { if (active) { setError(true); setNotice(aiExtractionMessageFor(reason)) } })
      .finally(() => { if (active) setBusy(false) })
    return () => { active = false }
  }, [load, refreshVersion])

  function change(change: Partial<AiExtractionFilters>) {
    setBusy(true)
    setData(null)
    setError(false)
    setNotice('')
    setFilters((current) => nextAiExtractionFilters(current, change))
  }
  function refresh() {
    setBusy(true)
    setData(null)
    setError(false)
    setNotice('')
    setRefreshVersion((current) => current + 1)
  }
  function formatDate(value: string | null) { return value ? new Date(value).toLocaleString('ko-KR') : '—' }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (submitInFlight.current) return
    setSubmittedResult(null)
    setFieldErrors({})
    const nextFieldErrors = aiExtractionSubmissionFieldErrors(videoUrl, supplementText)
    if (Object.keys(nextFieldErrors).length) {
      setFieldErrors(nextFieldErrors)
      setSubmitError(true)
      setSubmitNotice('입력값을 확인해 주세요.')
      if (nextFieldErrors.videoUrl) videoUrlInput.current?.focus()
      else supplementTextInput.current?.focus()
      return
    }

    submitInFlight.current = true
    setSubmitBusy(true)
    try {
      const fingerprint = `${videoUrl.trim()}\n${supplementText.trim()}`
      const attempt = aiExtractionSubmissionAttempt(submissionAttempt.current, fingerprint, () => crypto.randomUUID())
      submissionAttempt.current = attempt
      setSubmitError(false)
      setSubmitNotice('AI 영상 추출 작업을 접수하는 중입니다.')
      const result = await createAiVideoExtraction(videoUrl, supplementText, attempt.key)
      setSubmitNotice(result.reused
        ? '기존 AI 영상 추출 작업을 다시 안내했습니다. 작업 목록에서 최신 상태를 확인해 주세요.'
        : 'AI 영상 추출 작업을 접수했습니다. 작업 목록에서 진행 상태를 확인해 주세요.')
      setSubmittedResult(result)
      setVideoUrl('')
      setSupplementText('')
      submissionAttempt.current = null
      setRefreshVersion((current) => current + 1)
    } catch (reason) {
      setSubmitError(true)
      const message = aiExtractionMessageFor(reason, 'submission')
      setSubmitNotice(message)
      if (reason instanceof AdminApiError && reason.code === 'AIEXTRACT_INVALID_VIDEO_URL') {
        setFieldErrors({ videoUrl: message })
        videoUrlInput.current?.focus()
      }
    } finally {
      submitInFlight.current = false
      setSubmitBusy(false)
    }
  }

  return <div className={styles.screen}>
    <section className={styles.panel} aria-labelledby="new-ai-extraction-heading">
      <div className={styles.toolbar}>
        <div>
          <p className={styles.sectionKicker}>작업 접수</p>
          <h2 id="new-ai-extraction-heading">신규 영상 추가</h2>
        </div>
        <p className={styles.meta}>관리자 입력은 새 비동기 작업으로 수렴합니다.</p>
      </div>
      <p className={styles.hint}>전송 범위: 공개 YouTube 영상 URL과 관리자가 입력한 보완 텍스트만 Google Gemini로 전송됩니다. 보완 텍스트는 암호화된 임시 입력으로 보존될 수 있으며 작업 종료 후 24시간 이내 삭제됩니다. 원본 영상·전체 자막·Provider 응답 전문은 저장하거나 화면에 다시 표시하지 않습니다.</p>
      <form className={styles.form} noValidate onSubmit={(event) => void submit(event)}>
        <label htmlFor="ai-video-url">YouTube 영상 URL</label>
        <input ref={videoUrlInput} id="ai-video-url" name="videoUrl" type="text" inputMode="url" required value={videoUrl} disabled={submitBusy} aria-invalid={fieldErrors.videoUrl ? 'true' : undefined} aria-describedby={fieldErrors.videoUrl ? 'ai-video-url-error' : undefined} onChange={(event) => { setVideoUrl(event.target.value); setFieldErrors((current) => ({ ...current, videoUrl: undefined })) }} autoComplete="url" />
        {fieldErrors.videoUrl ? <p id="ai-video-url-error" className={styles.fieldError} role="alert">{fieldErrors.videoUrl}</p> : null}
        <label htmlFor="ai-supplement-text">보완 텍스트 <span className={styles.meta}>(선택, 최대 20,000자)</span></label>
        <textarea ref={supplementTextInput} id="ai-supplement-text" name="supplementText" rows={5} value={supplementText} disabled={submitBusy} aria-invalid={fieldErrors.supplementText ? 'true' : undefined} aria-describedby={fieldErrors.supplementText ? 'ai-supplement-text-error' : undefined} onChange={(event) => { setSupplementText(event.target.value); setFieldErrors((current) => ({ ...current, supplementText: undefined })) }} />
        {fieldErrors.supplementText ? <p id="ai-supplement-text-error" className={styles.fieldError} role="alert">{fieldErrors.supplementText}</p> : null}
        <Button type="submit" disabled={submitBusy}>{submitBusy ? '접수 중…' : '추출 작업 접수'}</Button>
      </form>
      {submitNotice ? <p className={submitError ? styles.error : styles.notice} role={submitError ? 'alert' : 'status'} aria-live={submitError ? undefined : 'polite'}>{submitNotice}</p> : null}
      {submittedResult ? (() => {
        const presentation = aiExtractionSubmissionPresentation(submittedResult)
        return <>
          {presentation.statusLabel ? <p className={styles.meta}>{presentation.statusLabel}</p> : null}
          <Link className={styles.resultLink} href={`/admin/ai/${encodeURIComponent(submittedResult.jobId)}`}>{presentation.linkLabel}</Link>
        </>
      })() : null}
    </section>
    <section className={styles.panel} aria-labelledby="ai-extraction-filter-heading">
      <div className={styles.toolbar}>
        <div>
          <h2 id="ai-extraction-filter-heading">작업 목록</h2>
          <p className={styles.hint}>실행 상태와 자동 등록 상태를 분리해 확인할 수 있습니다.</p>
        </div>
        <Button variant="secondary" disabled={busy} onClick={refresh}>새로고침</Button>
      </div>
      <div className={styles.filterRow} aria-label="작업 목록 필터">
        <label>실행 상태<select value={filters.executionStatus} disabled={busy} onChange={(event) => change({ executionStatus: event.target.value as AiExecutionStatus | '' })}>
          <option value="">전체</option><option value="QUEUED">대기</option><option value="RUNNING">실행 중</option><option value="SUCCEEDED">성공</option><option value="FAILED">실패</option>
        </select></label>
        <label>유입 경로<select value={filters.source} disabled={busy} onChange={(event) => change({ source: event.target.value as AiExtractionSource | '' })}>
          <option value="">전체</option><option value="WEBHOOK">Webhook</option><option value="ADMIN">관리자</option>
        </select></label>
        <label>자동 등록 상태<select value={filters.reviewStatus} disabled={busy} onChange={(event) => change({ reviewStatus: event.target.value as AiExtractionReviewStatus | '' })}>
          <option value="">전체</option><option value="AUTO_CONFIRMED">자동 확정</option><option value="AUTO_BLOCKED">자동 보류</option><option value="AUTO_REJECTED">자동 거부</option><option value="MANUAL_OVERRIDE">수동 보정</option>
        </select></label>
      </div>
    </section>

    {busy && !data ? <StatePanel compact title="AI 작업 목록을 불러오는 중입니다" /> : null}
    {!busy && !error && data?.items.length === 0 ? <StatePanel compact title="조건에 맞는 AI 작업이 없습니다" description="필터를 바꾸거나 새로고침해 주세요." /> : null}
    {!busy && !error && data && data.items.length > 0 ? <ul className={styles.list}>{data.items.map((job) => <JobCard key={job.jobId} job={job} formatDate={formatDate} />)}</ul> : null}
    <nav className={styles.pagination} aria-label="AI 작업 목록 페이지">
      <Button variant="secondary" disabled={busy || !data || error || filters.page <= 1} onClick={() => change({ page: filters.page - 1 })}>이전</Button>
      <span>{filters.page} / {Math.max(data?.page.totalPages ?? 1, 1)} 페이지 <span className={styles.paginationTotal}>총 {data?.page.totalElements ?? 0}건</span></span>
      <Button variant="secondary" disabled={busy || !data || error || !data.page.hasNext} onClick={() => change({ page: filters.page + 1 })}>다음</Button>
    </nav>
    {notice ? <p className={error ? styles.error : styles.notice} role={error ? 'alert' : 'status'}>{notice}</p> : null}
  </div>
}

function JobCard({ job, formatDate }: { job: AiExtractionJob; formatDate: (value: string | null) => string }) {
  const summary = job.registrationSummary
  return <li className={styles.card}>
    <div className={styles.cardHeader}>
      <div className={styles.cardHeading}>
        <p className={styles.sectionKicker}>AI 추출 작업</p>
        <h2><Link href={`/admin/ai/${encodeURIComponent(job.jobId)}`}>작업 상세</Link></h2>
        <p className={styles.meta}>작업 ID <code>{job.jobId}</code></p>
      </div>
    </div>
    <div className={styles.cardSource}>
      <a className={styles.videoLink} href={job.youtube.videoUrl} target="_blank" rel="noreferrer">영상 {job.youtube.videoId} 확인</a>
      <span>유입 경로: {aiSourceLabel(job.source)}</span>
    </div>
    <div className={styles.statusSummary} aria-label="AI 작업 상태 요약">
      <div className={styles.statusItem}>
        <span className={styles.statusItemLabel}>실행 상태</span>
        <div className={styles.statusItemValue}><StatusBadge tone={aiExecutionTone(job.executionStatus)}>{aiExecutionLabel(job.executionStatus)}</StatusBadge><span className={styles.code}>{job.executionStatus}</span></div>
      </div>
      <div className={styles.statusItem}>
        <span className={styles.statusItemLabel}>결과 완전성</span>
        <div className={styles.statusItemValue}><StatusBadge tone={aiCompletenessTone(job.resultCompleteness)}>{aiCompletenessLabel(job.resultCompleteness)}</StatusBadge>{job.resultCompleteness ? <span className={styles.code}>{job.resultCompleteness}</span> : null}</div>
      </div>
      <div className={styles.statusItem}>
        <span className={styles.statusItemLabel}>자동 등록 상태</span>
        <div className={styles.statusItemValue}><StatusBadge tone={aiReviewTone(job.reviewStatus)}>{aiReviewLabel(job.reviewStatus)}</StatusBadge>{job.reviewStatus ? <span className={styles.code}>{job.reviewStatus}</span> : null}</div>
      </div>
    </div>
    {summary ? <dl className={styles.registrationSummary} aria-label="등록 결과 집계">
      <div className={styles.summaryItem}><dt>등록 단위</dt><dd>{summary.total}개</dd></div>
      <div className={styles.summaryItem}><dt>현재 등록</dt><dd><span>{summary.registered}개</span><span>{summary.total === 0 ? '등록 단위 없음' : summary.registered === summary.total ? '전체 등록' : summary.partialRegistration ? '부분 등록' : '현재 등록 없음'}</span></dd></div>
      <div className={styles.summaryItem}><dt>자동 확정</dt><dd>{summary.autoConfirmed}개</dd></div>
      <div className={styles.summaryItem}><dt>자동 보류</dt><dd>{summary.autoBlocked}개</dd></div>
      <div className={styles.summaryItem}><dt>자동 거부 / 수동 보정</dt><dd>{summary.autoRejected}개 / {summary.manualOverride}개</dd></div>
    </dl> : null}
    {job.executionStatus === 'FAILED' ? <p className={styles.warning}><span>실패 사유: {aiDiagnosticLabel(job.failureDiagnostic, job.failureDiagnostic ? null : '미기록') ?? '세부 원인 미기록'}</span>{aiDiagnosticAction(job.failureDiagnostic) ? <span>{aiDiagnosticAction(job.failureDiagnostic)}</span> : null}</p> : null}
    <dl className={styles.metaGrid}>
      <div className={styles.metaItem}><dt>버전</dt><dd>{job.provider} / {job.modelVersion} / {job.promptVersion} / {job.schemaVersion}</dd></div>
      <div className={styles.metaItem}><dt>시도</dt><dd>{job.attemptCount}회</dd></div>
      <div className={styles.metaItem}><dt>생성</dt><dd>{formatDate(job.createdAt)}</dd></div>
      <div className={styles.metaItem}><dt>완료</dt><dd>{formatDate(job.finishedAt)}</dd></div>
    </dl>
  </li>
}
