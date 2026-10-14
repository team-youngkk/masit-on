import type { Metadata } from 'next'

import { PageShell } from '@/components/ui/PageShell'
import { isDesignPreviewEnvironment } from '@/lib/design-preview'

import { CourseScreen } from './CourseScreen'
import styles from './course.module.css'

type CoursePageProps = {
  searchParams: Promise<Record<string, string | string[] | undefined>>
}

function firstValue(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? '' : value ?? ''
}

export const metadata: Metadata = {
  title: '맛집 코스 | 맛잇온',
  description: '선택한 맛집으로 자동차 이동 순서와 구간별 경로를 확인합니다.',
}

/*
 * 코스 경로 결과는 서버가 조회 시점 응답만 주는 API 계약(restaurant-course-recommendation-api.md 1절)에
 * 따라 화면 안 상태로 유지하고, 검색 조건만 URL에서 초기화·공유한다. 이 파일은 라우트 진입점만 맡는다.
 */
export default async function CoursePage({ searchParams }: CoursePageProps) {
  const rawParams = await searchParams
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
        <CourseScreen
          designPreview={isDesignPreview}
          initialFilters={{
            query: firstValue(rawParams.query),
            regionCode: firstValue(rawParams.regionCode),
            category: firstValue(rawParams.category),
          }}
        />
      </section>
    </PageShell>
  )
}
