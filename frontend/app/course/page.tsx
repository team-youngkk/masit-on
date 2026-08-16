import type { Metadata } from 'next'

import { PageShell } from '@/components/ui/PageShell'

import { CourseScreen } from './CourseScreen'
import styles from './course.module.css'

export const metadata: Metadata = {
  title: '맛집 코스 | 맛잇온',
  description: '선택한 맛집으로 자동차 이동 순서와 구간별 경로를 확인합니다.',
}

/*
 * 코스 화면 전체(검색·선택·계산·결과·실패)는 URL 쿼리로 공유하지 않는 화면 안 상태이며
 * 서버가 조회 시점 응답만 주는 API 계약(restaurant-course-recommendation-api.md 1절)과
 * 맞물려 클라이언트 컴포넌트 하나가 상태를 소유한다. 이 파일은 라우트 진입점만 맡는다.
 */
export default function CoursePage() {
  return (
    <PageShell
      className={styles.page}
      eyebrow="자동차 이동 코스"
      title="맛집 코스"
      description="2~5곳을 선택하면 첫 맛집을 출발점으로 자동차 이동 순서를 제안합니다."
    >
      <CourseScreen />
    </PageShell>
  )
}
