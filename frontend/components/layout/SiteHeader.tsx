'use client'

import Link from 'next/link'
import { usePathname, useSearchParams } from 'next/navigation'
import { Suspense, useEffect, useRef, useState } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { buildMapNavigationHref } from '@/lib/map/map-navigation'

import { Brand } from './Brand'
import styles from './SiteHeader.module.css'

function MapNavigationLink() {
  const pathname = usePathname()
  const searchParams = useSearchParams()

  return <Link href={buildMapNavigationHref(pathname, searchParams)}>지도</Link>
}

export function SiteHeader() {
  const { status, logout } = useMemberSession()
  const menuRef = useRef<HTMLDetailsElement>(null)
  const [logoutFailed, setLogoutFailed] = useState(false)

  useEffect(() => {
    if (status === 'authenticated') {
      setLogoutFailed(false)
    }
  }, [status])

  function closeMemberMenu(): void {
    menuRef.current?.removeAttribute('open')
  }

  function handleMemberMenuKeyDown(event: React.KeyboardEvent<HTMLDetailsElement>): void {
    if (event.key !== 'Escape') {
      return
    }
    closeMemberMenu()
    menuRef.current?.querySelector('summary')?.focus()
  }

  async function handleLogout(): Promise<void> {
    setLogoutFailed(false)
    closeMemberMenu()
    try {
      await logout()
    } catch (reason) {
      if (!(reason instanceof Response) || reason.status !== 401) {
        setLogoutFailed(true)
      }
    }
  }

  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link href="/restaurants" className={styles.brandLink}>
          <Brand />
        </Link>
        <nav className={styles.nav} aria-label="주요 메뉴">
          <Link href="/restaurants">맛집 탐색</Link>
          <Suspense fallback={<Link href="/map">지도</Link>}>
            <MapNavigationLink />
          </Suspense>
          {status === 'loading' ? (
            <span className={styles.sessionLoading} aria-live="polite">
              로그인 확인 중
            </span>
          ) : null}
          {status === 'anonymous' ? <Link href="/login">로그인</Link> : null}
          {status === 'authenticated' ? (
            <details
              className={styles.memberMenu}
              ref={menuRef}
              onKeyDown={handleMemberMenuKeyDown}
            >
              <summary>내 메뉴</summary>
              <div className={styles.memberMenuItems}>
                <Link href="/me/favorites" onClick={closeMemberMenu}>
                  내 찜
                </Link>
                <Link href="/me/recent-restaurants" onClick={closeMemberMenu}>
                  최근 본 맛집
                </Link>
                <Link href="/me" onClick={closeMemberMenu}>
                  내 계정
                </Link>
                <button type="button" onClick={() => void handleLogout()}>
                  로그아웃
                </button>
              </div>
            </details>
          ) : null}
          {logoutFailed ? (
            <div className={styles.logoutError} role="alert">
              <span>서버 로그아웃을 완료하지 못했습니다.</span>
              <button type="button" onClick={() => void handleLogout()}>
                다시 시도
              </button>
              <Link href="/restaurants" onClick={() => setLogoutFailed(false)}>
                공개 화면
              </Link>
            </div>
          ) : null}
        </nav>
      </div>
    </header>
  )
}
