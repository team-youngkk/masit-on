'use client'

import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { getAiVideoExtractions, type AiExtractionJob, type AiExtractionPage, type AiExecutionStatus, type AiExtractionReviewStatus, type AiExtractionSource } from '@/lib/admin/ai-video-extractions'
import { nextAiExtractionFilters, type AiExtractionFilters } from '@/lib/admin/ai-video-extractions-coordination'
import { aiExtractionMessageFor } from '@/lib/admin/ai-video-extractions'

import styles from './AiVideoExtractionScreen.module.css'

const initialFilters: AiExtractionFilters = { executionStatus: '', source: '', reviewStatus: '', page: 1 }

export function AiVideoExtractionList() {
  const [filters, setFilters] = useState(initialFilters)
  const [data, setData] = useState<AiExtractionPage | null>(null)
  const [busy, setBusy] = useState(true)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState(false)
  const requestId = useRef(0)

  const load = useCallback(async () => {
    const currentRequest = ++requestId.current
    const next = await getAiVideoExtractions(filters)
    if (currentRequest === requestId.current) setData(next)
  }, [filters])

  useEffect(() => {
    let active = true
    setBusy(true)
    void load().then(() => { if (active) { setError(false); setNotice('') } })
      .catch((reason) => { if (active) { setError(true); setNotice(aiExtractionMessageFor(reason)) } })
      .finally(() => { if (active) setBusy(false) })
    return () => { active = false }
  }, [load])

  function change(change: Partial<AiExtractionFilters>) { setFilters((current) => nextAiExtractionFilters(current, change)) }
  function formatDate(value: string | null) { return value ? new Date(value).toLocaleString('ko-KR') : '—' }

  return <div className={styles.screen}>
    <div className={styles.toolbar}>
      <div className={styles.filterRow} aria-label="작업 목록 필터">
        <label>실행 상태<select value={filters.executionStatus} disabled={busy} onChange={(event) => change({ executionStatus: event.target.value as AiExecutionStatus | '' })}>
          <option value="">전체</option><option value="QUEUED">대기</option><option value="RUNNING">실행 중</option><option value="SUCCEEDED">성공</option><option value="FAILED">실패</option>
        </select></label>
        <label>유입 경로<select value={filters.source} disabled={busy} onChange={(event) => change({ source: event.target.value as AiExtractionSource | '' })}>
          <option value="">전체</option><option value="WEBHOOK">Webhook</option><option value="ADMIN">관리자</option>
        </select></label>
        <label>검수 상태<select value={filters.reviewStatus} disabled={busy} onChange={(event) => change({ reviewStatus: event.target.value as AiExtractionReviewStatus | '' })}>
          <option value="">전체</option><option value="AUTO_CONFIRMED">자동 확정</option><option value="AUTO_BLOCKED">자동 보류</option><option value="AUTO_REJECTED">자동 거부</option><option value="MANUAL_OVERRIDE">수동 보정</option>
        </select></label>
      </div>
      <Button variant="secondary" disabled={busy} onClick={() => void load()}>새로고침</Button>
    </div>

    {busy && !data ? <p role="status">AI 작업 목록을 불러오는 중입니다.</p> : null}
    {!busy && !error && data?.items.length === 0 ? <p className={styles.notice}>조건에 맞는 AI 작업이 없습니다.</p> : null}
    {data?.items.length ? <ul className={styles.list}>{data.items.map((job) => <JobCard key={job.jobId} job={job} formatDate={formatDate} />)}</ul> : null}
    <nav className={styles.pagination} aria-label="AI 작업 목록 페이지">
      <Button variant="secondary" disabled={busy || filters.page <= 1} onClick={() => change({ page: filters.page - 1 })}>이전</Button>
      <span>{filters.page} / {Math.max(data?.page.totalPages ?? 1, 1)} 페이지 · 총 {data?.page.totalElements ?? 0}건</span>
      <Button variant="secondary" disabled={busy || !data?.page.hasNext} onClick={() => change({ page: filters.page + 1 })}>다음</Button>
    </nav>
    {notice ? <p className={error ? styles.error : styles.notice} role={error ? 'alert' : 'status'}>{notice}</p> : null}
  </div>
}

function JobCard({ job, formatDate }: { job: AiExtractionJob; formatDate: (value: string | null) => string }) {
  return <li className={styles.card}>
    <span className={styles.badge}>{job.executionStatus}</span>
    <h2><Link href={`/admin/ai/${encodeURIComponent(job.jobId)}`}>작업 상세</Link></h2>
    <p className={styles.meta}><code>{job.jobId}</code></p>
    <p className={styles.meta}>유입 {job.source} · 검수 {job.reviewStatus ?? '미정'} · 결과 {job.resultCompleteness ?? '미완료'}</p>
    <p className={styles.meta}>버전 {job.modelVersion} / {job.promptVersion} / {job.schemaVersion} · 시도 {job.attemptCount}회</p>
    <p className={styles.meta}>생성 {formatDate(job.createdAt)} · 완료 {formatDate(job.finishedAt)}</p>
  </li>
}
