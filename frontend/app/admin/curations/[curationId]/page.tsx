import { AdminCurationDetail } from '@/components/admin/AdminCurationDetail'
import { AdminPage } from '@/components/admin/AdminPage'

export default async function AdminCurationDetailPage({ params }: { params: Promise<{ curationId: string }> }) {
  const { curationId } = await params
  return <AdminPage title="큐레이션 편집" wide><AdminCurationDetail curationId={curationId} /></AdminPage>
}
