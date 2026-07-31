import { AdminPage } from '@/components/admin/AdminPage'
import { RegistrationFlow } from '@/components/admin/RegistrationFlow'

export default function VideoRegistrationPage() {
  return (
    <AdminPage title="영상 등록">
      <RegistrationFlow
        resourceName="영상"
        previewPath="/api/admin/video-registration-previews"
        createPath="/api/admin/videos"
        inputs={[{ name: 'sourceUrl', label: '유튜브 영상 URL', type: 'url' }]}
      />
    </AdminPage>
  )
}
