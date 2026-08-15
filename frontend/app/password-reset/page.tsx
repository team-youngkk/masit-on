import { MemberAuthForm } from '@/components/member/MemberAuthForm'
import { PageShell } from '@/components/ui/PageShell'
import styles from './page.module.css'

export default function PasswordResetPage() {
  return (
    <PageShell className={styles.page} size="narrow" eyebrow="회원" title="비밀번호 재설정" description="재설정 메일을 요청하거나 받은 토큰으로 새 비밀번호를 설정하세요.">
      <div className={styles.forms}>
        <MemberAuthForm mode="request-reset" />
        <MemberAuthForm mode="confirm-reset" />
      </div>
    </PageShell>
  )
}
