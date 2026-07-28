import { AdminPage } from '@/components/admin/AdminPage'
import { RegistrationFlow } from '@/components/admin/RegistrationFlow'

export default function CreatorRegistrationPage() {
  return (
    <AdminPage title="유튜버 등록">
      <RegistrationFlow
        resourceName="유튜버"
        previewPath="/api/admin/creator-registration-previews"
        createPath="/api/admin/creators"
        inputs={[{ name: 'channelUrl', label: '유튜브 채널 URL', type: 'url' }]}
      />
    </AdminPage>
  )
}
