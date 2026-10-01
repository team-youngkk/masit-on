import { AdminPage } from '@/components/admin/AdminPage'
import { RegistrationFlow } from '@/components/admin/RegistrationFlow'

export default function RestaurantRegistrationPage() {
  return (
    <AdminPage title="맛집 등록">
      <p>전국 맛집을 등록할 수 있습니다. 지역은 카카오에서 검증한 도로명주소로 자동 연결됩니다. 미리보기에서 주소와 지역을 확인해 주세요.</p>
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
