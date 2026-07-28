import { AdminPage } from '@/components/admin/AdminPage'
import { RegistrationFlow } from '@/components/admin/RegistrationFlow'

export default function RestaurantRegistrationPage() {
  return (
    <AdminPage title="맛집 등록">
      <RegistrationFlow
        resourceName="맛집"
        previewPath="/api/admin/restaurant-registration-previews"
        createPath="/api/admin/restaurants"
        inputs={[
          { name: 'name', label: '맛집 이름' },
          { name: 'kakaoPlaceUrl', label: '카카오 장소 URL', type: 'url' },
          { name: 'roadAddress', label: '도로명 주소' },
          { name: 'detailAddress', label: '상세 주소', required: false },
          { name: 'phoneNumber', label: '전화번호', type: 'tel' },
          { name: 'category', label: '음식 카테고리' },
        ]}
      />
    </AdminPage>
  )
}
