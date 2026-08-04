import { safeVerificationReturnTo } from '@/lib/verification/login'

import styles from './page.module.css'
import { VerificationLoginForm } from './VerificationLoginForm'

type VerificationLoginPageProps = {
  searchParams: Promise<{ returnTo?: string | string[] }>
}

export default async function VerificationLoginPage({ searchParams }: VerificationLoginPageProps) {
  const rawReturnTo = (await searchParams).returnTo
  const returnTo = safeVerificationReturnTo(
    typeof rawReturnTo === 'string' ? rawReturnTo : undefined,
  )

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="verification-login-title">
        <p className={styles.eyebrow}>제한 공개</p>
        <h1 id="verification-login-title">검증 참여자 로그인</h1>
        <p className={styles.description}>
          맛잇온 검증에 참여하도록 안내받은 계정으로 로그인해 주세요.
        </p>
        <VerificationLoginForm returnTo={returnTo} />
      </section>
    </main>
  )
}
