import { MemberAuthForm } from '@/components/member/MemberAuthForm'
export default function PasswordResetPage() {
  return <section><h1>Password reset</h1><MemberAuthForm mode="request-reset" /><MemberAuthForm mode="confirm-reset" /></section>
}
