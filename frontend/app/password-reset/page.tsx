import { MemberAuthForm } from '@/components/member/MemberAuthForm'
export default async function PasswordResetPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const { token } = await searchParams
  return <section><h1>Password reset</h1><MemberAuthForm mode={token ? 'confirm-reset' : 'request-reset'} token={token} /></section>
}
