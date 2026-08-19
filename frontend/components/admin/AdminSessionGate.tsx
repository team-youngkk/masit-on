'use client'

import type { ReactNode } from 'react'
import { useEffect, useState } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { StatePanel } from '@/components/ui/StatePanel'
import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { safeAdminReturnTo } from '@/lib/member/auth-navigation'
import styles from './admin.module.css'

export function AdminSessionGate({ children }: { children: ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const { status, session, refreshSession } = useMemberSession()
  const [retried, setRetried] = useState(false)
  useEffect(() => {
    if (status !== 'loading' || retried) return
    setRetried(true)
    void refreshSession()
  }, [status, retried, refreshSession])
  useEffect(() => {
    if (status !== 'anonymous') return
    const returnTo = safeAdminReturnTo(pathname) ?? '/admin'
    router.replace(`/login?${new URLSearchParams({ returnTo }).toString()}`)
  }, [pathname, router, status])
  if (status === 'loading') return <p className={styles.loading} aria-live="polite">관리자 인증을 확인하고 있습니다.</p>
  if (status === 'unavailable') return <StatePanel tone="warning" title="인증 상태를 확인하지 못했습니다" description="잠시 후 다시 시도해 주세요." actions={<button type="button" onClick={() => void refreshSession()}>다시 시도</button>} />
  if (status === 'anonymous') return null
  if (session?.role !== 'ADMIN') return <StatePanel tone="danger" title="관리자 권한이 필요합니다" description="현재 계정으로는 관리자 화면에 접근할 수 없습니다." />
  return <>{children}</>
}
