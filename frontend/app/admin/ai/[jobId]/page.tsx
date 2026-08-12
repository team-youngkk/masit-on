import { AiVideoExtractionDetail } from '@/components/admin/AiVideoExtractionDetail'
import { AdminPage } from '@/components/admin/AdminPage'

export default async function AdminAiVideoExtractionDetailPage({ params }: { params: Promise<{ jobId: string }> }) {
  const { jobId } = await params
  return <AdminPage title="AI 영상 추출 작업" wide><AiVideoExtractionDetail jobId={jobId} /></AdminPage>
}
