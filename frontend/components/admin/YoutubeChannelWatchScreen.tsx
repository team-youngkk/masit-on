'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { StatePanel } from '@/components/ui/StatePanel'
import { StatusBadge } from '@/components/ui/StatusBadge'
import {
  getVerifiedCreators,
  getYoutubeChannelWatch,
  setYoutubeChannelWatchEnabled,
  youtubeChannelWatchMessageFor,
  youtubeChannelWatchQueryKey,
  type YoutubeChannelWatchStatus,
} from '@/lib/admin/youtube-channel-watches'
import {
  watchEnabledLabel,
  watchErrorCategoryLabel,
  watchErrorMessage,
  watchStatusPresentation,
  watchToggleEnabled,
  watchToggleLabel,
} from '@/lib/admin/youtube-channel-watches-coordination'

import styles from './YoutubeChannelWatchScreen.module.css'

export function YoutubeChannelWatchScreen() {
  const [selectedCreatorId, setSelectedCreatorId] = useState('')
  const [notice, setNotice] = useState<string | null>(null)
  const [noticeIsError, setNoticeIsError] = useState(false)
  const queryClient = useQueryClient()

  const creatorsQuery = useQuery({
    queryKey: ['admin', 'verified-creators'],
    queryFn: getVerifiedCreators,
  })
  const creators = creatorsQuery.data ?? []

  useEffect(() => {
    if (creators.length > 0 && !creators.some((creator) => creator.id === selectedCreatorId)) {
      setSelectedCreatorId(creators[0].id)
    }
  }, [creators, selectedCreatorId])

  const watchQuery = useQuery({
    queryKey: youtubeChannelWatchQueryKey(selectedCreatorId),
    queryFn: () => getYoutubeChannelWatch(selectedCreatorId),
    enabled: Boolean(selectedCreatorId),
  })

  const mutation = useMutation({
    mutationFn: ({ creatorId, enabled }: { creatorId: string; enabled: boolean }) =>
      setYoutubeChannelWatchEnabled(creatorId, enabled),
    onSuccess: (status, variables) => {
      queryClient.setQueryData(youtubeChannelWatchQueryKey(variables.creatorId), status)
      setNotice('감시 상태를 저장했고 최신 상태를 반영했습니다.')
      setNoticeIsError(false)
    },
    onError: async (error, variables) => {
      setNotice(youtubeChannelWatchMessageFor(error))
      setNoticeIsError(true)
      try {
        await queryClient.refetchQueries({ queryKey: youtubeChannelWatchQueryKey(variables.creatorId) })
      } catch {
        // 원래 실패 안내는 유지하고, 재조회 실패는 다음 수동 조회에서 복구한다.
      }
    },
  })

  function selectCreator(creatorId: string) {
    setNotice(null)
    setSelectedCreatorId(creatorId)
  }

  function toggleWatch() {
    if (!selectedCreatorId || !watchQuery.data || mutation.isPending) return
    setNotice(null)
    mutation.mutate({ creatorId: selectedCreatorId, enabled: watchToggleEnabled(watchQuery.data) })
  }

  if (creatorsQuery.isPending) {
    return <StatePanel title="검증된 유튜버 목록을 불러오는 중입니다." />
  }

  if (creatorsQuery.isError) {
    return <StatePanel
      tone="danger"
      title="유튜버 목록을 불러오지 못했습니다."
      description={youtubeChannelWatchMessageFor(creatorsQuery.error)}
      actions={<Button className={styles.retryButton} variant="secondary" onClick={() => void creatorsQuery.refetch()}>다시 조회</Button>}
    />
  }

  if (creators.length === 0) {
    return <StatePanel title="감시할 검증된 유튜버가 없습니다." description="먼저 공개·검증된 유튜버를 등록해 주세요." />
  }

  return <div className={styles.screen}>
    <section className={styles.creatorPanel} aria-labelledby="youtube-watch-creator-heading">
      <label htmlFor="youtube-watch-creator" id="youtube-watch-creator-heading">
        감시할 유튜버
        <select
          id="youtube-watch-creator"
          value={selectedCreatorId}
          disabled={mutation.isPending}
          onChange={(event) => selectCreator(event.target.value)}
        >
          {creators.map((creator) => <option key={creator.id} value={creator.id}>{creator.channelName}</option>)}
        </select>
      </label>
      <p className={styles.hint}>검증된 Creator만 선택할 수 있습니다. 채널 감시는 신규 YouTube 영상 Webhook 접수 여부만 제어합니다.</p>
    </section>

    {watchQuery.isPending ? <StatePanel title="채널 감시 상태를 불러오는 중입니다." /> : null}
    {watchQuery.isError ? <StatePanel tone="danger" title="채널 감시 상태를 조회하지 못했습니다." description={youtubeChannelWatchMessageFor(watchQuery.error)} actions={<Button className={styles.retryButton} variant="secondary" onClick={() => void watchQuery.refetch()}>다시 조회</Button>} /> : null}
    {watchQuery.data ? <WatchStatusPanel status={watchQuery.data} busy={mutation.isPending} onToggle={toggleWatch} /> : null}
    {notice ? <p className={noticeIsError ? styles.errorNotice : styles.notice} role={noticeIsError ? 'alert' : 'status'}>{notice}</p> : null}
  </div>
}

function WatchStatusPanel({
  status,
  busy,
  onToggle,
}: {
  status: YoutubeChannelWatchStatus
  busy: boolean
  onToggle: () => void
}) {
  const presentation = watchStatusPresentation(status.subscriptionStatus)
  const errorMessage = watchErrorMessage(status.lastErrorCategory)

  return <section className={styles.statusPanel} aria-labelledby="youtube-watch-status-heading">
    <div className={styles.statusHeader}>
      <div className={styles.statusTitle}>
        <h2 id="youtube-watch-status-heading">채널 감시 상태</h2>
        <StatusBadge tone={presentation.tone}>{presentation.label}</StatusBadge>
      </div>
      <div className={styles.statusActions}>
        <span className={styles.meta}>{watchEnabledLabel(status.enabled)}</span>
        <Button disabled={busy} onClick={onToggle}>{busy ? '저장 중…' : watchToggleLabel(status)}</Button>
      </div>
    </div>
    <p className={styles.statusDescription}>{presentation.description}</p>
    {errorMessage ? <p className={styles.errorNotice}>{errorMessage}</p> : null}
    <dl className={styles.detailGrid}>
      <div className={styles.detail}><dt>마지막 오류 범주</dt><dd>{watchErrorCategoryLabel(status.lastErrorCategory)}</dd></div>
      <div className={styles.detail}><dt>마지막 오류 시각</dt><dd>{formatDate(status.lastErrorAt)}</dd></div>
      <div className={styles.detail}><dt>마지막 Webhook 수신</dt><dd>{formatDate(status.lastNotificationAt)}</dd></div>
      <div className={styles.detail}><dt>마지막 구독 갱신</dt><dd>{formatDate(status.lastRenewedAt)}</dd></div>
    </dl>
  </section>
}

function formatDate(value: string | null): string {
  if (!value) return '기록 없음'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? '기록 없음' : parsed.toLocaleString('ko-KR')
}
