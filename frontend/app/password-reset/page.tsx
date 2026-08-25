'use client'

import { useEffect, useState } from 'react'
import { MemberAuthForm } from '@/components/member/MemberAuthForm'
import { watchPasswordResetMode, type MemberAuthMode } from '@/components/member/member-auth-form-coordination'
import { PageShell } from '@/components/ui/PageShell'
import styles from './page.module.css'

export default function PasswordResetPage() {
  const [mode, setMode] = useState<Extract<MemberAuthMode, 'request-reset' | 'confirm-reset'> | null>(null)

  useEffect(() => {
    return watchPasswordResetMode(
      () => window.location.hash,
      setMode,
      listener => {
        window.addEventListener('hashchange', listener)
        return () => window.removeEventListener('hashchange', listener)
      },
    )
  }, [])

  return (
    <PageShell className={styles.page} size="narrow" eyebrow="회원" title="비밀번호 재설정" description="재설정 메일을 요청하거나 메일의 링크로 새 비밀번호를 설정하세요.">
      <div className={styles.forms}>
        {mode ? <MemberAuthForm mode={mode} /> : null}
      </div>
    </PageShell>
  )
}
