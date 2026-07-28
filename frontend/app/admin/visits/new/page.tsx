import { AdminPage } from '@/components/admin/AdminPage'
import { VisitRegistrationForm } from '@/components/admin/VisitRegistrationForm'

export default function VisitRegistrationPage() {
  return (
    <AdminPage title="방문 관계 등록">
      <VisitRegistrationForm />
    </AdminPage>
  )
}
