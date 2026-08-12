import { AiVideoExtractionList } from '@/components/admin/AiVideoExtractionList'
import { AdminPage } from '@/components/admin/AdminPage'

export default function AdminAiVideoExtractionsPage() {
  return <AdminPage title="AI 영상 추출 관리" wide><AiVideoExtractionList /></AdminPage>
}
