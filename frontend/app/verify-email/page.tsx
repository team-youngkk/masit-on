import { VerifyEmail } from '@/components/member/VerifyEmail'

export default async function VerifyEmailPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const { token } = await searchParams
  return <section><h1>Email verification</h1><VerifyEmail token={token} /></section>
}
