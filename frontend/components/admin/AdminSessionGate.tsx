'use client'

import type { ReactNode } from 'react'
import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'

import { ADMIN_AUTH_EXPIRED_EVENT, ensureAdminSession } from '@/lib/admin/auth'

import styles from './admin.module.css'

export function AdminSessionGate({ children }: { children: ReactNode }) {
  const router = useRouter()
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let active = true

    const redirectToLogin = () => {
      if (active) {
        router.replace('/admin/login')
      }
    }

    window.addEventListener(ADMIN_AUTH_EXPIRED_EVENT, redirectToLogin)

    void ensureAdminSession().then((authenticated) => {
      if (!active) {
        return
      }
      if (!authenticated) {
        redirectToLogin()
        return
      }
      setReady(true)
    })

    return () => {
      active = false
      window.removeEventListener(ADMIN_AUTH_EXPIRED_EVENT, redirectToLogin)
    }
  }, [router])

  if (!ready) {
    return <p className={styles.loading}>관리자 인증을 확인하고 있습니다.</p>
  }

  return <>{children}</>
}
