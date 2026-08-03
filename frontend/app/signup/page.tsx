import Link from 'next/link'

import { MemberAuthForm } from '@/components/member/MemberAuthForm'
import { memberLoginHref, memberVerifyEmailHref } from '@/lib/member/auth-navigation'

type SignupPageProps = {
  searchParams: Promise<{ returnTo?: string | string[] }>
}

export default async function SignupPage({ searchParams }: SignupPageProps) {
  const rawReturnTo = (await searchParams).returnTo
  const returnTo = typeof rawReturnTo === 'string' ? rawReturnTo : undefined

  return (
    <section>
      <h1>Create account</h1>
      <MemberAuthForm mode="signup" returnTo={returnTo} />
      <p>
        Already have an account? <Link href={memberLoginHref(returnTo)}>Sign in</Link>
      </p>
      <p>
        인증 토큰을 받으셨나요? <Link href={memberVerifyEmailHref(returnTo)}>이메일 인증</Link>
      </p>
    </section>
  )
}
