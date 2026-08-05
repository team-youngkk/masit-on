'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { useState } from 'react'

import { logout } from '@/lib/admin/auth'
import { Button } from '@/components/ui/Button'

import styles from './admin.module.css'

const items = [
  { href: '/admin/participation', label: '제보·신고 검토' },
  { href: '/admin/restaurants/new', label: '맛집 등록' },
  { href: '/admin/creators/new', label: '유튜버 등록' },
  { href: '/admin/videos/new', label: '영상 등록' },
  { href: '/admin/visits/new', label: '방문 관계 등록' },
]

export function AdminNavigation() {
  const pathname = usePathname()
  const router = useRouter()
  const [logoutError, setLogoutError] = useState<string | null>(null)

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
    <header className={styles.adminHeader}>
      <nav className={styles.navigation} aria-label="관리자 메뉴">
        {items.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className={pathname === item.href ? styles.activeLink : styles.link}
          >
            {item.label}
          </Link>
        ))}
      </nav>
      <div>
        <Button variant="secondary" onClick={() => void handleLogout()}>
          로그아웃
        </Button>
        {logoutError ? <p className={styles.error} role="alert">{logoutError}</p> : null}
      </div>
    </header>
  )
}
