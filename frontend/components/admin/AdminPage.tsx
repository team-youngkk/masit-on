'use client'

import type { ReactNode } from 'react'

import { AdminNavigation } from './AdminNavigation'
import { AdminSessionGate } from './AdminSessionGate'
import { PageShell } from '@/components/ui/PageShell'
import styles from './admin.module.css'

export function AdminPage({
  title,
  children,
  wide = false,
}: {
  title: string
  children: ReactNode
  wide?: boolean
}) {
  return (
    <AdminSessionGate>
      <section className={styles.shell}>
        <AdminNavigation />
        <main className={styles.main}>
          <PageShell title={title} className={wide ? styles.wideContent : styles.content}>
            {children}
          </PageShell>
        </main>
      </section>
    </AdminSessionGate>
  )
}
