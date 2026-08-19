'use client'

import Link from 'next/link'
import { usePathname, useSearchParams } from 'next/navigation'
import { Suspense, useEffect, useRef, useState } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { buildMapNavigationHref } from '@/lib/map/map-navigation'
import { COURSE_NAVIGATION } from '@/lib/course/course-navigation'
import { cn } from '@/lib/cn'

import { Brand } from './Brand'
import { NotificationBell } from './NotificationBell'
import styles from './SiteHeader.module.css'

function MapNavigationLink({
  className,
  children = '지도',
}: {
  className?: string
  children?: React.ReactNode
}) {
  const pathname = usePathname()
  const searchParams = useSearchParams()

  return (
    <Link
      href={buildMapNavigationHref(pathname, searchParams)}
      className={className}
      aria-current={pathname.startsWith('/map') ? 'page' : undefined}
    >
      {children}
    </Link>
  )
}

export function SiteHeader() {
  const pathname = usePathname()
  const { status, session, logout } = useMemberSession()
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

  function navClass(href: string): string {
    return cn(styles.navLink, pathname === href || pathname.startsWith(`${href}/`) ? styles.active : undefined)
  }

  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link href="/restaurants" className={styles.brandLink}>
          <Brand />
        </Link>
        <nav className={styles.desktopNav} aria-label="주요 메뉴">
          <Link href="/restaurants" className={navClass('/restaurants')} aria-current={pathname.startsWith('/restaurants') ? 'page' : undefined}>맛집 탐색</Link>
          <Link href={COURSE_NAVIGATION.href} className={navClass(COURSE_NAVIGATION.href)} aria-current={pathname.startsWith(COURSE_NAVIGATION.href) ? 'page' : undefined}>{COURSE_NAVIGATION.label}</Link>
          <Link href="/popular" className={navClass('/popular')} aria-current={pathname.startsWith('/popular') ? 'page' : undefined}>인기</Link>
          <Link href="/curations" className={navClass('/curations')} aria-current={pathname.startsWith('/curations') ? 'page' : undefined}>큐레이션</Link>
          <Suspense fallback={<Link href="/map" className={navClass('/map')}>지도</Link>}>
            <MapNavigationLink className={navClass('/map')} />
          </Suspense>
        </nav>
        <div className={styles.accountArea}>
          {status === 'loading' ? (
            <span className={styles.sessionLoading} aria-live="polite">
              로그인 확인 중
            </span>
          ) : null}
          {status === 'anonymous' ? <Link href="/login">로그인</Link> : null}
          {status === 'authenticated' ? (
            <>
              {session?.role === 'ADMIN' ? <Link href="/admin">관리자</Link> : null}
              <NotificationBell />
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
                  <Link href="/me/collections" onClick={closeMemberMenu}>
                    내 컬렉션
                  </Link>
                  <Link href="/me/requests" onClick={closeMemberMenu}>
                    내 제보·신고
                  </Link>
                  <Link href="/me" onClick={closeMemberMenu}>
                    내 계정
                  </Link>
                  <button type="button" onClick={() => void handleLogout()}>
                    로그아웃
                  </button>
                </div>
              </details>
            </>
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
        </div>
      </div>
      <nav className={styles.mobileNav} aria-label="모바일 주요 메뉴">
        <Link href="/restaurants" className={navClass('/restaurants')} aria-current={pathname.startsWith('/restaurants') ? 'page' : undefined}>
          <span aria-hidden="true">⌕</span><span>탐색</span>
        </Link>
        <Link href={COURSE_NAVIGATION.href} className={navClass(COURSE_NAVIGATION.href)} aria-current={pathname.startsWith(COURSE_NAVIGATION.href) ? 'page' : undefined}>
          <span aria-hidden="true">↝</span><span>코스</span>
        </Link>
        <Link href="/popular" className={navClass('/popular')} aria-current={pathname.startsWith('/popular') ? 'page' : undefined}>
          <span aria-hidden="true">♨</span><span>인기</span>
        </Link>
        <Link href="/curations" className={navClass('/curations')} aria-current={pathname.startsWith('/curations') ? 'page' : undefined}>
          <span aria-hidden="true">▣</span><span>큐레이션</span>
        </Link>
        <Suspense fallback={<Link href="/map" className={navClass('/map')}><span aria-hidden="true">⌖</span><span>지도</span></Link>}>
          <MapNavigationLink className={navClass('/map')}><span aria-hidden="true">⌖</span><span>지도</span></MapNavigationLink>
        </Suspense>
      </nav>
    </header>
  )
}
