'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { useState } from 'react'

import { logout } from '@/lib/admin/auth'
import { Button } from '@/components/ui/Button'

import styles from './admin.module.css'

const items = [
  { href: '/admin', label: '대시보드', icon: '⌂' },
  { href: '/admin/participation', label: '제보·신고 검토', icon: '▣' },
  { href: '/admin/curations', label: '큐레이션 관리', icon: '▤' },
  { href: '/admin/ai', label: 'AI 영상 추출 관리', icon: '▷' },
  { href: '/admin/ai/youtube-channel-watches', label: 'YouTube 채널 감시', icon: '◉' },
  { href: '/admin/restaurants/new', label: '맛집 등록', icon: '⌁' },
  { href: '/admin/creators/new', label: '유튜버 등록', icon: '♙' },
  { href: '/admin/videos/new', label: '영상 등록', icon: '▹' },
  { href: '/admin/visits/new', label: '방문 관계 등록', icon: '⌖' },
]

export function AdminNavigation() {
  const pathname = usePathname()
  const router = useRouter()
  const [logoutError, setLogoutError] = useState<string | null>(null)
  const activeHref = items
    .filter((item) => item.href === '/admin'
      ? pathname === item.href
      : pathname === item.href || pathname.startsWith(`${item.href}/`))
    .sort((left, right) => right.href.length - left.href.length)[0]?.href

  async function handleLogout() {
    setLogoutError(null)
    try {
      await logout()
      router.replace('/admin/login')
    } catch {
      setLogoutError('로그아웃을 완료하지 못했습니다. 네트워크를 확인한 뒤 다시 시도해 주세요.')
    }
  }

  return (
    <aside className={styles.sidebar}>
      <Link className={styles.brand} href="/admin" aria-label="맛잇온 관리자 대시보드">맛잇온 <span>Admin</span></Link>
      <p className={styles.navigationLabel}>관리 메뉴</p>
      <nav className={styles.navigation} aria-label="관리자 메뉴">
        {items.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className={item.href === activeHref ? styles.activeLink : styles.link}
            aria-current={item.href === activeHref ? 'page' : undefined}
          >
            <span className={styles.navIcon} aria-hidden="true">{item.icon}</span>
            <span>{item.label}</span>
          </Link>
        ))}
      </nav>
      <div className={styles.sidebarFooter}>
        <Link className={styles.link} href="/restaurants">
          <span className={styles.navIcon} aria-hidden="true">↗</span>
          <span>메인 페이지</span>
        </Link>
        <Button variant="secondary" onClick={() => void handleLogout()}>
          로그아웃
        </Button>
        {logoutError ? <p className={styles.error} role="alert">{logoutError}</p> : null}
      </div>
    </aside>
  )
}
