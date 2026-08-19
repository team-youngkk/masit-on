import Link from 'next/link'

import { MemberAuthForm } from '@/components/member/MemberAuthForm'
import { PageShell } from '@/components/ui/PageShell'
import { memberSignupHref } from '@/lib/member/auth-navigation'

import styles from './page.module.css'

type LoginPageProps = {
  searchParams: Promise<{ returnTo?: string | string[] }>
}

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const rawReturnTo = (await searchParams).returnTo
  const returnTo = typeof rawReturnTo === 'string' ? rawReturnTo : undefined

  return (
    <PageShell className={styles.page} size="narrow" eyebrow="회원" title="로그인" description="이메일과 비밀번호로 로그인하세요.">
      <MemberAuthForm mode="login" returnTo={returnTo} />
      <p className={styles.signupPrompt}>
        계정이 없으신가요?{' '}
        <Link className={styles.signupLink} href={memberSignupHref(returnTo)}>
          회원가입
        </Link>
      </p>
    </PageShell>
  )
}
