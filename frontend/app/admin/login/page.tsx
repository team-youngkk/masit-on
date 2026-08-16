import { LoginForm } from '@/components/admin/LoginForm'
import { PageShell } from '@/components/ui/PageShell'

import styles from '@/components/admin/admin.module.css'

export default function AdminLoginPage() {
  return (
    <main className={styles.loginPage}>
      <PageShell
        size="narrow"
        eyebrow="masit-on Admin"
        title="관리자 로그인"
        description="관리자 계정으로 로그인해 영상 검수와 등록 작업을 진행하세요."
      >
        <LoginForm />
      </PageShell>
    </main>
  )
}
