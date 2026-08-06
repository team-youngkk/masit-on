'use client'

import type { ReactNode } from 'react'

import { AdminNavigation } from './AdminNavigation'
import { AdminSessionGate } from './AdminSessionGate'
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
      <section className={styles.page}>
        <AdminNavigation />
        <div className={wide ? styles.wideContent : styles.content}>
          <h1>{title}</h1>
          {children}
        </div>
      </section>
    </AdminSessionGate>
  )
}
