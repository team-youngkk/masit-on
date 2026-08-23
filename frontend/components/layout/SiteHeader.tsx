'use client'

import Link from 'next/link'
import { usePathname, useSearchParams } from 'next/navigation'
import { Suspense, useEffect, useRef, useState } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { buildMapNavigationHref } from '@/lib/map/map-navigation'
import { COURSE_NAVIGATION } from '@/lib/course/course-navigation'
import { THEME_STORAGE_KEY, isTheme, resolveTheme, type Theme } from '@/lib/theme'
import { cn } from '@/lib/cn'

import { Brand } from './Brand'
import { NotificationBell } from './NotificationBell'
import styles from './SiteHeader.module.css'

function MapNavigationLink({
  className,
  onClick,
  children = '지도',
}: {
  className?: string
  onClick?: () => void
  children?: React.ReactNode
}) {
  const pathname = usePathname()
  const searchParams = useSearchParams()

  return (
    <Link
      href={buildMapNavigationHref(pathname, searchParams)}
      className={className}
      onClick={onClick}
      aria-current={pathname.startsWith('/map') ? 'page' : undefined}
    >
      {children}
    </Link>
  )
}

type MobileNavIconName = 'search' | 'course' | 'popular' | 'curation' | 'map'

function MobileNavIcon({ name }: { name: MobileNavIconName }) {
  const props = {
    viewBox: '0 0 24 24',
    width: 24,
    height: 24,
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.8,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true,
  }

  if (name === 'search') {
    return <svg {...props} strokeWidth={2.1}><circle cx="10" cy="10" r="6.5" /><path d="m15 15 5 5" /></svg>
  }

  if (name === 'course') {
    return <svg {...props}><path d="M5 20c4 0 5-3 5-6s1-6 5-6h4" /><path d="m16 5 3-3 3 3" /></svg>
  }

  if (name === 'popular') {
    return <svg {...props}><path d="M7 4h10v3a5 5 0 0 1-10 0V4Z" /><path d="M7 6H4v1a4 4 0 0 0 4 4M17 6h3v1a4 4 0 0 1-4 4M12 12v4M9 20h6M10 16h4" /></svg>
  }

  if (name === 'curation') {
    return <svg {...props}><path d="m4 7 8-3 8 3-8 3-8-3Z" /><path d="m4 12 8 3 8-3M4 17l8 3 8-3" /></svg>
  }

  return <svg {...props}><path d="m3.5 5.5 5.5-2.5 6 3 5.5-2.5v15L15 21l-6-3-5.5 2.5v-15Z" /><path d="M9 3v15M15 6v15" /></svg>
}

export function SiteHeader() {
  const pathname = usePathname()
  const { status, session, logout } = useMemberSession()
  const menuRef = useRef<HTMLDetailsElement>(null)
  const quickMenuRef = useRef<HTMLDetailsElement>(null)
  const [logoutFailed, setLogoutFailed] = useState(false)
  const [theme, setTheme] = useState<Theme>('light')
  const [themeReady, setThemeReady] = useState(false)

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')

    function applyTheme(nextTheme: Theme): void {
      document.documentElement.dataset.theme = nextTheme
      setTheme(nextTheme)
    }

    function syncTheme(): void {
      let storedTheme: string | null = null
      try {
        storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
      } catch {
        // 저장소 접근이 차단된 환경에서는 시스템 설정만 사용한다.
      }
      applyTheme(resolveTheme(storedTheme, mediaQuery.matches))
      setThemeReady(true)
    }

    function handleStorage(event: StorageEvent): void {
      if (event.key === THEME_STORAGE_KEY) {
        syncTheme()
      }
    }

    function handleSystemThemeChange(): void {
      let hasStoredTheme = false
      try {
        hasStoredTheme = isTheme(window.localStorage.getItem(THEME_STORAGE_KEY))
      } catch {
        // 시스템 설정 변경을 그대로 반영한다.
      }
      if (!hasStoredTheme) {
        syncTheme()
      }
    }

    syncTheme()
    window.addEventListener('storage', handleStorage)
    mediaQuery.addEventListener?.('change', handleSystemThemeChange)

    return () => {
      window.removeEventListener('storage', handleStorage)
      mediaQuery.removeEventListener?.('change', handleSystemThemeChange)
    }
  }, [])

  function toggleTheme(): void {
    const nextTheme: Theme = theme === 'dark' ? 'light' : 'dark'
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, nextTheme)
    } catch {
      // 현재 탭의 테마 전환은 저장소가 없어도 동작해야 한다.
    }
    document.documentElement.dataset.theme = nextTheme
    setTheme(nextTheme)
    setThemeReady(true)
  }

  useEffect(() => {
    if (status === 'authenticated') {
      setLogoutFailed(false)
    }
  }, [status])

  useEffect(() => {
    quickMenuRef.current?.removeAttribute('open')
  }, [pathname])

  function closeMemberMenu(): void {
    menuRef.current?.removeAttribute('open')
  }

  function closeQuickMenu(): void {
    quickMenuRef.current?.removeAttribute('open')
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
        <div className={styles.headerTools} aria-label="헤더 도구">
          <button
            type="button"
            className={styles.themeToggle}
            onClick={toggleTheme}
            aria-hidden={!themeReady}
            aria-label={themeReady ? (theme === 'dark' ? '라이트 모드로 전환' : '다크 모드로 전환') : '테마 변경 준비 중'}
            aria-pressed={themeReady && theme === 'dark'}
            disabled={!themeReady}
            tabIndex={themeReady ? 0 : -1}
            title={themeReady ? (theme === 'dark' ? '라이트 모드' : '다크 모드') : '테마 변경'}
          >
            <span aria-hidden="true" className={styles.themeIcon}>
              {themeReady ? (theme === 'dark' ? (
                <svg className={styles.moonIcon} viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M19.9 14.5A8.5 8.5 0 1 1 9.5 4.1a7 7 0 0 0 10.4 10.4Z" />
                </svg>
              ) : <span className={styles.sunIcon}>☀</span>) : '◐'}
            </span>
          </button>
          {status === 'authenticated' ? <NotificationBell /> : null}
          <details className={styles.quickMenu} ref={quickMenuRef}>
            <summary aria-label="메뉴">
              <span className={styles.menuIcon} aria-hidden="true"><i /><i /><i /></span>
            </summary>
            <nav className={styles.quickMenuItems} aria-label="빠른 메뉴 목록">
              <Link href="/restaurants" onClick={closeQuickMenu}>맛집 탐색</Link>
              <Link href={COURSE_NAVIGATION.href} onClick={closeQuickMenu}>{COURSE_NAVIGATION.label}</Link>
              <Link href="/popular" onClick={closeQuickMenu}>인기</Link>
              <Link href="/curations" onClick={closeQuickMenu}>큐레이션</Link>
              <Suspense fallback={<Link href="/map" onClick={closeQuickMenu}>지도</Link>}>
                <MapNavigationLink onClick={closeQuickMenu}>지도</MapNavigationLink>
              </Suspense>
              <span className={styles.quickMenuDivider} aria-hidden="true" />
              {status === 'loading' ? (
                <span className={styles.quickMenuStatus}>로그인 확인 중</span>
              ) : null}
              {status === 'anonymous' || status === 'unavailable' ? <Link href="/login" onClick={closeQuickMenu}>로그인</Link> : null}
              {status === 'authenticated' ? (
                <>
                  {session?.role === 'ADMIN' ? <Link href="/admin" onClick={closeQuickMenu}>관리자</Link> : null}
                  <NotificationBell showLabel />
                  <Link href="/me" onClick={closeQuickMenu}>내 정보</Link>
                  <Link href="/me/favorites" onClick={closeQuickMenu}>내 찜</Link>
                  <Link href="/me/recent-restaurants" onClick={closeQuickMenu}>최근 본 맛집</Link>
                  <Link href="/me/collections" onClick={closeQuickMenu}>내 컬렉션</Link>
                  <Link href="/me/requests" onClick={closeQuickMenu}>내 제보·신고</Link>
                  <button type="button" onClick={() => void handleLogout()}>로그아웃</button>
                </>
              ) : null}
            </nav>
          </details>
        </div>
        <div className={styles.accountArea}>
          {status === 'loading' ? (
            <span className={styles.sessionLoading} aria-live="polite">
              로그인 확인 중
            </span>
          ) : null}
          {status === 'anonymous' || status === 'unavailable' ? <Link href="/login">로그인</Link> : null}
          {status === 'authenticated' ? (
            <>
              {session?.role === 'ADMIN' ? <Link href="/admin">관리자</Link> : null}
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
        </div>
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
      <nav className={styles.mobileNav} aria-label="모바일 주요 메뉴">
        <Link href="/restaurants" className={navClass('/restaurants')} aria-current={pathname.startsWith('/restaurants') ? 'page' : undefined}>
          <MobileNavIcon name="search" /><span>탐색</span>
        </Link>
        <Link href={COURSE_NAVIGATION.href} className={navClass(COURSE_NAVIGATION.href)} aria-current={pathname.startsWith(COURSE_NAVIGATION.href) ? 'page' : undefined}>
          <MobileNavIcon name="course" /><span>코스</span>
        </Link>
        <Link href="/popular" className={navClass('/popular')} aria-current={pathname.startsWith('/popular') ? 'page' : undefined}>
          <MobileNavIcon name="popular" /><span>인기</span>
        </Link>
        <Link href="/curations" className={navClass('/curations')} aria-current={pathname.startsWith('/curations') ? 'page' : undefined}>
          <MobileNavIcon name="curation" /><span>큐레이션</span>
        </Link>
        <Suspense fallback={<Link href="/map" className={navClass('/map')}><MobileNavIcon name="map" /><span>지도</span></Link>}>
          <MapNavigationLink className={navClass('/map')}><MobileNavIcon name="map" /><span>지도</span></MapNavigationLink>
        </Suspense>
      </nav>
    </header>
  )
}

