import { AdminCurationList } from '@/components/admin/AdminCurationList'
import { AdminPage } from '@/components/admin/AdminPage'

export default function AdminCurationsPage() {
  return <AdminPage title="큐레이션 관리" wide><AdminCurationList /></AdminPage>
}
