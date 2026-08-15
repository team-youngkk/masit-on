import { VerifyEmail } from '@/components/member/VerifyEmail'
import { PageShell } from '@/components/ui/PageShell'
import { memberLoginHref } from '@/lib/member/auth-navigation'

type VerifyEmailPageProps = {
  searchParams: Promise<{ returnTo?: string | string[] }>
}

export default async function VerifyEmailPage({ searchParams }: VerifyEmailPageProps) {
  const rawReturnTo = (await searchParams).returnTo
  const returnTo = typeof rawReturnTo === 'string' ? rawReturnTo : undefined

  return (
    <PageShell size="narrow">
      <VerifyEmail loginHref={memberLoginHref(returnTo)} />
    </PageShell>
  )
}
