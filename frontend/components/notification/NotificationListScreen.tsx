'use client'

import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'
import { memberLoginHref } from '@/lib/member/auth-navigation'
import {
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  NotificationItem,
  notificationErrorMessage,
  parseNotificationError,
} from '@/lib/member/notifications'
import {
  clearItemNotice,
  markAllItemsRead,
  setNotificationRead,
  shouldApplyResponse,
} from '@/lib/member/notifications-coordination'
import {
  getParticipationDetail,
  ParticipationItem,
  parseParticipationError,
} from '@/lib/member/participation'
import {
  participationTargetDetails,
  participationTargetSummary,
} from '@/lib/member/participation-coordination'

import styles from './NotificationListScreen.module.css'

const RETURN_TO = '/me/notifications'
const PAGE_SIZE = 20

type Notice = { text: string; isError: boolean; traceId?: string }

function formatCreatedAt(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

export function NotificationListScreen() {
  const { status: session } = useMemberSession()

  // 목록 조회 상태 — 개별 읽음/전체 읽음/상세 조회와 절대 공유하지 않는다.
  const [items, setItems] = useState<NotificationItem[]>([])
  const [pageNumber, setPageNumber] = useState(1)
  const [totalPages, setTotalPages] = useState(0)
  const [hasNext, setHasNext] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const [listBusy, setListBusy] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const [listError, setListError] = useState<Notice | null>(null)
  const [unauthorized, setUnauthorized] = useState(false)

  // 개별 읽음 처리 상태 — 알림 하나당 하나의 오류만 갖는다.
  const [pendingReadIds, setPendingReadIds] = useState<Set<string>>(new Set())
  const [itemNotices, setItemNotices] = useState<Record<string, Notice>>({})

  // 전체 읽음 처리 상태.
  const [markAllBusy, setMarkAllBusy] = useState(false)
  const [markAllNotice, setMarkAllNotice] = useState<Notice | null>(null)

  // 인라인 상세(연관 제보·신고) 조회 상태.
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [detailBusy, setDetailBusy] = useState(false)
  const [detailError, setDetailError] = useState<Notice | null>(null)
  const [detailUnavailable, setDetailUnavailable] = useState(false)
  const [detailItem, setDetailItem] = useState<ParticipationItem | null>(null)

  const listRequest = useRef(0)
  const detailRequest = useRef(0)
  const readEpoch = useRef(0)

  const load = useCallback(async () => {
    if (session !== 'authenticated') return
    const request = ++listRequest.current
    setListBusy(true)
    try {
      const page = await getNotifications(pageNumber, PAGE_SIZE)
      if (!shouldApplyResponse(request, listRequest.current)) return
      setItems(page.items)
      setPageNumber(page.page.number)
      setTotalPages(page.page.totalPages)
      setHasNext(page.page.hasNext)
      setUnreadCount(page.unreadCount)
      setListError(null)
      setUnauthorized(false)
    } catch (reason) {
      const parsed = await parseNotificationError(reason)
      if (!shouldApplyResponse(request, listRequest.current)) return
      if (parsed?.status === 401) {
        setUnauthorized(true)
        setListError(null)
      } else {
        setUnauthorized(false)
        setListError({
          text: parsed
            ? notificationErrorMessage(parsed.status, parsed.contract)
            : '알림 목록을 불러오지 못했습니다. 다시 시도해 주세요.',
          isError: true,
          traceId: parsed?.contract.traceId,
        })
      }
    } finally {
      if (shouldApplyResponse(request, listRequest.current)) {
        setListBusy(false)
        setLoaded(true)
      }
    }
  }, [pageNumber, session])

  useEffect(() => { void load() }, [load])

  async function markRead(item: NotificationItem) {
    if (item.read) return
    const epoch = readEpoch.current
    setItems((prev) => setNotificationRead(prev, item.notificationId, true))
    setUnreadCount((prev) => Math.max(0, prev - 1))
    setPendingReadIds((prev) => new Set(prev).add(item.notificationId))
    setItemNotices((prev) => clearItemNotice(prev, item.notificationId))
    try {
      await markNotificationRead(item.notificationId)
    } catch (reason) {
      if (!shouldApplyResponse(epoch, readEpoch.current)) return
      setItems((prev) => setNotificationRead(prev, item.notificationId, false))
      setUnreadCount((prev) => prev + 1)
      const parsed = await parseNotificationError(reason)
      if (!shouldApplyResponse(epoch, readEpoch.current)) return
      if (parsed?.status === 401) {
        setUnauthorized(true)
      } else {
        setItemNotices((prev) => ({
          ...prev,
          [item.notificationId]: {
            text: parsed
              ? notificationErrorMessage(parsed.status, parsed.contract)
              : '읽음 처리에 실패했습니다. 다시 시도해 주세요.',
            isError: true,
            traceId: parsed?.contract.traceId,
          },
        }))
      }
    } finally {
      setPendingReadIds((prev) => {
        const next = new Set(prev)
        next.delete(item.notificationId)
        return next
      })
    }
  }

  async function handleMarkAllRead() {
    setMarkAllBusy(true)
    setMarkAllNotice({ text: '처리 중입니다...', isError: false })
    try {
      const result = await markAllNotificationsRead()
      readEpoch.current += 1
      setItems((prev) => markAllItemsRead(prev))
      setUnreadCount(result.unreadCount)
      setItemNotices({})
      setPendingReadIds(new Set())
      setMarkAllNotice({ text: '모든 알림을 읽음으로 표시했습니다.', isError: false })
    } catch (reason) {
      const parsed = await parseNotificationError(reason)
      if (parsed?.status === 401) {
        setUnauthorized(true)
        setMarkAllNotice(null)
        return
      }
      setMarkAllNotice({
        text: parsed
          ? notificationErrorMessage(parsed.status, parsed.contract)
          : '전체 읽음 처리에 실패했습니다. 다시 시도해 주세요.',
        isError: true,
        traceId: parsed?.contract.traceId,
      })
    } finally {
      setMarkAllBusy(false)
    }
  }

  async function toggleDetail(item: NotificationItem) {
    if (expandedId === item.notificationId) {
      detailRequest.current += 1
      setExpandedId(null)
      return
    }

    const request = ++detailRequest.current
    setExpandedId(item.notificationId)
    setDetailItem(null)
    setDetailError(null)
    setDetailUnavailable(false)
    setDetailBusy(true)
    try {
      const kind = item.requestType === 'SUBMISSION' ? 'submission' : 'report'
      const detail = await getParticipationDetail(kind, item.requestId)
      if (!shouldApplyResponse(request, detailRequest.current)) return
      setDetailItem(detail)
    } catch (reason) {
      if (reason instanceof Response && reason.status === 404) {
        if (!shouldApplyResponse(request, detailRequest.current)) return
        setDetailUnavailable(true)
      } else {
        const parsed = await parseParticipationError(reason)
        if (!shouldApplyResponse(request, detailRequest.current)) return
        if (parsed?.status === 401) {
          setUnauthorized(true)
          setExpandedId(null)
        } else {
          setDetailError({
            text: parsed?.contract.message || '관련 요청 상세를 불러오지 못했습니다.',
            isError: true,
            traceId: parsed?.contract.traceId,
          })
        }
      }
    } finally {
      if (shouldApplyResponse(request, detailRequest.current)) setDetailBusy(false)
    }
  }

  function handleItemClick(item: NotificationItem) {
    if (!item.read) void markRead(item)
    void toggleDetail(item)
  }

  if (session === 'loading') {
    return <PageShell title="알림"><StatePanel title="로그인 상태를 확인하고 있습니다." /></PageShell>
  }
  if (session === 'anonymous') {
    return (
      <PageShell title="알림"><StatePanel tone="warning" title="알림은 로그인 후 확인할 수 있습니다." actions={<Link href={memberLoginHref(RETURN_TO)}>로그인하기</Link>} /></PageShell>
    )
  }

  const showNav = !unauthorized && loaded && totalPages > 0

  return (
    <PageShell className={styles.screen} title="알림" description="제보·신고 처리 상태를 확인합니다." actions={unauthorized || listError ? null : (
          <Button
            variant="secondary"
            disabled={markAllBusy || unreadCount <= 0}
            onClick={() => void handleMarkAllRead()}
          >
            {markAllBusy ? '처리 중...' : '모두 읽음으로 표시'}
          </Button>
        )}>

      {markAllNotice ? (
        <p
          className={markAllNotice.isError ? styles.error : styles.notice}
          role={markAllNotice.isError ? 'alert' : 'status'}
        >
          {markAllNotice.text}
          {markAllNotice.traceId ? <span className={styles.traceId}>traceId: {markAllNotice.traceId}</span> : null}
        </p>
      ) : null}

      {unauthorized ? (
        <p role="alert" className={styles.state}>
          로그인이 필요합니다. <Link href={memberLoginHref(RETURN_TO)}>로그인하기</Link>
        </p>
      ) : listError ? (
        <p role="alert" className={styles.error}>
          {listError.text}
          {listError.traceId ? <span className={styles.traceId}>traceId: {listError.traceId}</span> : null}
          {' '}
          <Button variant="secondary" disabled={listBusy} onClick={() => void load()}>다시 시도</Button>
        </p>
      ) : !loaded ? (
        <p role="status" className={styles.state}>알림을 불러오는 중입니다.</p>
      ) : items.length === 0 ? (
        <p role="status" className={styles.state}>아직 알림이 없습니다.</p>
      ) : (
        <ul className={styles.list}>
          {items.map((item) => {
            const busy = pendingReadIds.has(item.notificationId)
            const notice = itemNotices[item.notificationId]
            const expanded = expandedId === item.notificationId
            return (
              <li
                key={item.notificationId}
                className={item.read ? styles.itemRead : styles.itemUnread}
                aria-busy={busy}
              >
                <button
                  type="button"
                  className={styles.item}
                  aria-expanded={expanded}
                  aria-controls={`notification-detail-${item.notificationId}`}
                  onClick={() => handleItemClick(item)}
                >
                  <strong>{item.title}</strong>
                  <span>{item.message}</span>
                  <time dateTime={item.createdAt}>{formatCreatedAt(item.createdAt)}</time>
                </button>

                {notice ? (
                  <p role="alert" className={styles.error}>
                    {notice.text}
                    {notice.traceId ? <span className={styles.traceId}>traceId: {notice.traceId}</span> : null}
                  </p>
                ) : null}

                {expanded ? (
                  <div
                    id={`notification-detail-${item.notificationId}`}
                    className={styles.detail}
                    aria-live="polite"
                    aria-busy={detailBusy}
                  >
                    {detailBusy ? (
                      <p role="status">관련 요청을 불러오는 중입니다.</p>
                    ) : detailUnavailable ? (
                      <p role="status">이동 불가: 대상 요청을 더 이상 찾을 수 없습니다.</p>
                    ) : detailError ? (
                      <p role="alert">
                        {detailError.text}
                        {detailError.traceId ? <span className={styles.traceId}>traceId: {detailError.traceId}</span> : null}
                      </p>
                    ) : detailItem ? (
                      <>
                        <p><strong>대상:</strong> {participationTargetSummary(detailItem)}</p>
                        <p><strong>상태:</strong> {detailItem.status}</p>
                        {participationTargetDetails(detailItem).map(([label, value]) => (
                          <p key={label}><strong>{label}:</strong> {value}</p>
                        ))}
                        <p>{detailItem.description}</p>
                        {detailItem.memberReason ? <p><strong>처리 사유:</strong> {detailItem.memberReason}</p> : null}
                      </>
                    ) : null}
                  </div>
                ) : null}
              </li>
            )
          })}
        </ul>
      )}

      {showNav ? (
        <nav className={styles.actions} aria-label="알림 페이지">
          <Button variant="secondary" disabled={listBusy || pageNumber <= 1} onClick={() => setPageNumber((prev) => prev - 1)}>
            이전
          </Button>
          <span>{pageNumber} / {Math.max(totalPages, 1)} 페이지</span>
          <Button variant="secondary" disabled={listBusy || !hasNext} onClick={() => setPageNumber((prev) => prev + 1)}>
            다음
          </Button>
        </nav>
      ) : null}
    </PageShell>
  )
}
