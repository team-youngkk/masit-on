import { VerifyEmail } from '@/components/member/VerifyEmail'
import { memberLoginHref } from '@/lib/member/auth-navigation'

type VerifyEmailPageProps = {
  searchParams: Promise<{ returnTo?: string | string[] }>
}

export default async function VerifyEmailPage({ searchParams }: VerifyEmailPageProps) {
  const rawReturnTo = (await searchParams).returnTo
  const returnTo = typeof rawReturnTo === 'string' ? rawReturnTo : undefined

  return (
    <section>
      <VerifyEmail loginHref={memberLoginHref(returnTo)} />
    </section>
  )
}
