'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { Button } from '@/components/ui/Button'
import { StatePanel } from '@/components/ui/StatePanel'
import { StatusBadge } from '@/components/ui/StatusBadge'
import {
  getYoutubeChannelWatches,
  setYoutubeChannelWatchEnabled,
  youtubeChannelWatchMessageFor,
  youtubeChannelWatchesQueryKey,
  type YoutubeChannelWatchSummary,
} from '@/lib/admin/youtube-channel-watches'
import {
  watchEnabledLabel,
  watchErrorCategoryLabel,
  watchErrorMessage,
  watchStartAllowed,
  watchStatusPresentation,
  watchToggleEnabled,
  watchToggleLabel,
} from '@/lib/admin/youtube-channel-watches-coordination'

import styles from './YoutubeChannelWatchScreen.module.css'

const WATCH_PAGE_SIZE = 50

export function YoutubeChannelWatchScreen() {
  const [notice, setNotice] = useState<string | null>(null)
  const [noticeIsError, setNoticeIsError] = useState(false)
  const [pendingIds, setPendingIds] = useState<Set<string>>(() => new Set())
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({})
  const queryClient = useQueryClient()
  const queryKey = youtubeChannelWatchesQueryKey(1, WATCH_PAGE_SIZE)

  const watchesQuery = useQuery({
    queryKey,
    queryFn: () => getYoutubeChannelWatches(1, WATCH_PAGE_SIZE),
  })

  const mutation = useMutation({
    mutationFn: ({ creatorId, enabled }: { creatorId: string; enabled: boolean }) =>
      setYoutubeChannelWatchEnabled(creatorId, enabled),
    onMutate: ({ creatorId }) => {
      setPendingIds((current) => new Set(current).add(creatorId))
      setRowErrors((current) => {
        const next = { ...current }
        delete next[creatorId]
        return next
      })
    },
    onSuccess: (status, variables) => {
      queryClient.setQueryData(queryKey, (current: Awaited<ReturnType<typeof getYoutubeChannelWatches>> | undefined) => {
        if (!current) return current
        return {
          ...current,
          items: current.items.map((item) => item.creatorId === variables.creatorId ? { ...item, status } : item),
        }
      })
      setNotice('감시 상태를 저장했고 최신 상태를 반영했습니다.')
      setNoticeIsError(false)
    },
    onError: async (error, variables) => {
      const message = youtubeChannelWatchMessageFor(error)
      setRowErrors((current) => ({ ...current, [variables.creatorId]: message }))
      setNotice('일부 채널의 감시 상태를 저장하지 못했습니다. 실패한 행을 확인해 주세요.')
      setNoticeIsError(true)
      try {
        await queryClient.refetchQueries({ queryKey })
      } catch {
        // 행별 실패 안내는 유지하고, 다음 수동 조회에서 복구한다.
      }
    },
    onSettled: (_status, _error, variables) => {
      setPendingIds((current) => {
        const next = new Set(current)
        next.delete(variables.creatorId)
        return next
      })
    },
  })

  function toggleWatch(summary: YoutubeChannelWatchSummary) {
    if (pendingIds.has(summary.creatorId)) return
    const enabled = watchToggleEnabled(summary.status)
    if (enabled && !watchStartAllowed(summary)) return
    setNotice(null)
    mutation.mutate({ creatorId: summary.creatorId, enabled })
  }

  if (watchesQuery.isPending) {
    return <StatePanel title="유튜버 감시 목록을 불러오는 중입니다." />
  }

  if (watchesQuery.isError) {
    return <StatePanel
      tone="danger"
      title="유튜버 감시 목록을 불러오지 못했습니다."
      description={youtubeChannelWatchMessageFor(watchesQuery.error)}
      actions={<Button className={styles.retryButton} variant="secondary" onClick={() => void watchesQuery.refetch()}>다시 조회</Button>}
    />
  }

  const summaries = watchesQuery.data.items
  if (summaries.length === 0) {
    return <StatePanel title="감시할 유튜버가 없습니다." description="먼저 YouTube 채널이 연결된 Creator를 등록해 주세요." />
  }

  return <div className={styles.screen}>
    <section className={styles.creatorPanel} aria-labelledby="youtube-watch-creator-heading">
      <div className={styles.statusHeader}>
        <div>
          <h1 id="youtube-watch-creator-heading">YouTube 채널 감시</h1>
          <p className={styles.hint}>여러 유튜버를 동시에 감시할 수 있습니다. 각 행의 상태와 Webhook 수신 여부를 독립적으로 관리합니다.</p>
        </div>
        <Button className={styles.retryButton} variant="secondary" onClick={() => void watchesQuery.refetch()}>목록 새로고침</Button>
      </div>
    </section>

    <div className={styles.watchList}>
      {summaries.map((summary) => <WatchStatusPanel
        key={summary.creatorId}
        summary={summary}
        busy={pendingIds.has(summary.creatorId)}
        rowError={rowErrors[summary.creatorId] ?? null}
        onToggle={() => toggleWatch(summary)}
      />)}
    </div>
    {notice ? <p className={noticeIsError ? styles.errorNotice : styles.notice} role={noticeIsError ? 'alert' : 'status'}>{notice}</p> : null}
  </div>
}

function WatchStatusPanel({
  summary,
  busy,
  rowError,
  onToggle,
}: {
  summary: YoutubeChannelWatchSummary
  busy: boolean
  rowError: string | null
  onToggle: () => void
}) {
  const { status } = summary
  const presentation = watchStatusPresentation(status.subscriptionStatus)
  const errorMessage = watchErrorMessage(status.lastErrorCategory)
  const enabling = watchToggleEnabled(status)
  const canToggle = !enabling || watchStartAllowed(summary)
  const headingId = `youtube-watch-status-heading-${summary.creatorId}`

  return <section className={styles.statusPanel} aria-labelledby={headingId}>
    <div className={styles.statusHeader}>
      <div className={styles.statusTitle}>
        <div>
          <h2 id={headingId}>{summary.channelName}</h2>
          <p className={styles.availability}>{availabilityLabel(summary)}</p>
        </div>
        <StatusBadge tone={presentation.tone}>{presentation.label}</StatusBadge>
      </div>
      <div className={styles.statusActions}>
        <span className={styles.meta}>{watchEnabledLabel(status.enabled)}</span>
        <Button disabled={busy || !canToggle} onClick={onToggle}>
          {busy ? '저장 중…' : watchToggleLabel(status)}
        </Button>
      </div>
    </div>
    <p className={styles.statusDescription}>{presentation.description}</p>
    {!canToggle ? <p className={styles.hint}>공개·외부 이용 가능한 채널만 감시를 시작할 수 있습니다. 기존 감시는 중지할 수 있습니다.</p> : null}
    {errorMessage ? <p className={styles.errorNotice}>{errorMessage}</p> : null}
    {rowError ? <p className={styles.errorNotice} role="alert">{rowError}</p> : null}
    <dl className={styles.detailGrid}>
      <div className={styles.detail}><dt>마지막 오류 범주</dt><dd>{watchErrorCategoryLabel(status.lastErrorCategory)}</dd></div>
      <div className={styles.detail}><dt>마지막 오류 시각</dt><dd>{formatDate(status.lastErrorAt)}</dd></div>
      <div className={styles.detail}><dt>마지막 Webhook 수신</dt><dd>{formatDate(status.lastNotificationAt)}</dd></div>
      <div className={styles.detail}><dt>마지막 구독 갱신</dt><dd>{formatDate(status.lastRenewedAt)}</dd></div>
    </dl>
  </section>
}

function availabilityLabel(summary: YoutubeChannelWatchSummary): string {
  if (summary.publiclyVisible && summary.externallyAvailable) return '감시 시작 가능'
  if (summary.status.enabled) return '현재 감시 중 · 새로고침 후 중지 가능'
  return '현재 감시 시작 불가'
}

function formatDate(value: string | null): string {
  if (!value) return '기록 없음'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? '기록 없음' : parsed.toLocaleString('ko-KR')
}
