'use client'

import { usePathname } from 'next/navigation'
import type { ReactNode } from 'react'

import styles from '@/app/layout.module.css'
import { cn } from '@/lib/cn'

import { SiteFooter } from './SiteFooter'
import { SiteHeader } from './SiteHeader'

export function AppFrame({ children }: { children: ReactNode }) {
  const pathname = usePathname()
  const isAdmin = pathname === '/admin' || pathname.startsWith('/admin/')

  if (isAdmin) {
    return <div className={cn(styles.main, styles.adminMain)}>{children}</div>
  }

  return (
    <>
      <SiteHeader />
      <main className={styles.main}>{children}</main>
      <SiteFooter />
    </>
  )
}
