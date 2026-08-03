import Link from 'next/link'

import { MemberAuthForm } from '@/components/member/MemberAuthForm'
import { memberSignupHref } from '@/lib/member/auth-navigation'

import styles from './page.module.css'

type LoginPageProps = {
  searchParams: Promise<{ returnTo?: string | string[] }>
}

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const rawReturnTo = (await searchParams).returnTo
  const returnTo = typeof rawReturnTo === 'string' ? rawReturnTo : undefined

  return (
    <section className={styles.page}>
      <h1>Sign in</h1>
      <MemberAuthForm mode="login" />
      <p className={styles.signupPrompt}>
        계정이 없으신가요?{' '}
        <Link className={styles.signupLink} href={memberSignupHref(returnTo)}>
          회원가입
        </Link>
      </p>
    </section>
  )
}
