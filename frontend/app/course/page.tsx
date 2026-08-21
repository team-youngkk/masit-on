import type { Metadata } from 'next'

import { PageShell } from '@/components/ui/PageShell'
import { isDesignPreviewEnvironment } from '@/lib/design-preview'

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
  const isDesignPreview = isDesignPreviewEnvironment({
    nodeEnv: process.env.NODE_ENV,
    previewFlag: process.env.MASITON_UI_PREVIEW,
  })

  return (
    <PageShell
      className={styles.page}
      title="맛집 코스"
      description="2~5곳을 선택하세요."
    >
      <section className={styles.courseFrame} aria-label="맛집 코스 구성">
        <CourseScreen designPreview={isDesignPreview} />
      </section>
    </PageShell>
  )
}
