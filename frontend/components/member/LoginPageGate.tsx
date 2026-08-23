'use client'

import { useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'

import { PageShell } from '@/components/ui/PageShell'
import { Button } from '@/components/ui/Button'
import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { memberLoginDestination } from '@/lib/member/auth-navigation'
import { getLoginPageAction, shouldPreserveLoginForm } from '@/lib/member/login-page-navigation'

export function LoginPageGate({ children, returnTo }: { children: React.ReactNode; returnTo?: string | null }) {
  const router = useRouter()
  const { status, refreshSession } = useMemberSession()
  const action = getLoginPageAction(status)
  const destination = memberLoginDestination(returnTo)
  const formWasRendered = useRef(false)

  useEffect(() => {
    if (action === 'redirect') router.replace(destination)
  }, [action, destination, router])

  const preserveLoginForm = shouldPreserveLoginForm(action, formWasRendered.current)
  if (preserveLoginForm) {
    formWasRendered.current = true
    if (action === 'render') return children
    return (
      <>
        <div role={action === 'retry' ? 'alert' : 'status'} aria-live="polite">
          <p>
            {action === 'retry'
              ? '로그인 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.'
              : '로그인 상태를 확인하고 있습니다.'}
          </p>
          {action === 'retry'
            ? <Button type="button" variant="secondary" onClick={() => void refreshSession()}>다시 시도</Button>
            : null}
        </div>
        {children}
      </>
    )
  }

  return (
    <PageShell size="narrow" eyebrow="회원" title="로그인" description="이메일과 비밀번호로 로그인하세요.">
      <p role="status" aria-live="polite" aria-busy={action === 'wait'}>
        {action === 'wait' ? '로그인 상태를 확인하고 있습니다.' : '이미 로그인되어 있습니다. 맛집 화면으로 이동합니다.'}
      </p>
    </PageShell>
  )
}
