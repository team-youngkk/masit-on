import Link from 'next/link'

import { MemberAuthForm } from '@/components/member/MemberAuthForm'
import { PageShell } from '@/components/ui/PageShell'
import { memberLoginHref, memberVerifyEmailHref } from '@/lib/member/auth-navigation'

type SignupPageProps = {
  searchParams: Promise<{ returnTo?: string | string[] }>
}

export default async function SignupPage({ searchParams }: SignupPageProps) {
  const rawReturnTo = (await searchParams).returnTo
  const returnTo = typeof rawReturnTo === 'string' ? rawReturnTo : undefined

  return (
    <PageShell size="narrow" eyebrow="회원" title="회원가입" description="이메일 인증 후 찜과 최근 본 맛집을 계정에 연결할 수 있습니다.">
      <MemberAuthForm mode="signup" returnTo={returnTo} />
      <p>
        Already have an account? <Link href={memberLoginHref(returnTo)}>Sign in</Link>
      </p>
      <p>
        인증 토큰을 받으셨나요? <Link href={memberVerifyEmailHref(returnTo)}>이메일 인증</Link>
      </p>
    </PageShell>
  )
}
