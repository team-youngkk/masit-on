import { Card } from '@/components/ui/Card'

/*
 * 공통 레이아웃 확인용 자리표시 화면이다.
 * 검색·필터·목록과 페이지 이동은 T-05(WS-01)에서 구현한다.
 */
export default function RestaurantsPage() {
  return (
    <section>
      <h1>맛집 탐색</h1>
      <p>검색·필터·목록은 WS-01에서 구현합니다.</p>
      <Card title="공통 카드" level={2} meta="서울 중구 · 한식">
        맛집 카드의 기본 형태입니다.
      </Card>
    </section>
  )
}
