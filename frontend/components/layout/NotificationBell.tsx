'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { formatUnreadBadge, getUnreadCount } from '@/lib/member/notifications'

import styles from './SiteHeader.module.css'

export function NotificationBell() {
  const { status } = useMemberSession()
  const pathname = usePathname()
  const request = useRef(0)
  const [unreadCount, setUnreadCount] = useState(0)

  useEffect(() => {
    if (status !== 'authenticated') {
      request.current += 1
      setUnreadCount(0)
      return
    }

    const current = ++request.current
    void (async () => {
      try {
        const count = await getUnreadCount()
        if (current === request.current) setUnreadCount(count)
      } catch {
        // 헤더는 알림 조회 실패를 시끄럽게 보여줄 자리가 아니다 — 배지만 숨긴다.
        if (current === request.current) setUnreadCount(0)
      }
    })()
  }, [status, pathname])

  if (status !== 'authenticated') return null

  const badge = formatUnreadBadge(unreadCount)

  return (
    <Link
      href="/me/notifications"
      className={styles.notificationBell}
      aria-label={badge ? `읽지 않은 알림 ${unreadCount}개` : '알림'}
    >
      <span aria-hidden="true" className={styles.notificationIcon}>
        🔔
      </span>
      {badge ? (
        <span className={styles.notificationBadge} aria-hidden="true">
          {badge}
        </span>
      ) : null}
    </Link>
  )
}
