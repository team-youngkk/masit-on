import Link from 'next/link'

import { AdminPage } from '@/components/admin/AdminPage'
import { SectionHeader } from '@/components/ui/PageShell'
import { StatusBadge } from '@/components/ui/StatusBadge'

import styles from '@/components/admin/admin.module.css'

export default function AdminHomePage() {
  return (
    <AdminPage title="관리자 대시보드" wide>
      <section className={styles.dashboard} aria-labelledby="admin-dashboard-heading">
        <SectionHeader
          title={<span id="admin-dashboard-heading">관리 작업</span>}
          description="실제 등록·검수 작업으로 이동합니다. 집계 수치는 별도 API가 제공될 때만 표시합니다."
        />
        <div className={styles.dashboardGrid}>
          <DashboardLink href="/admin/ai" title="AI 영상 검수" description="영상 접수, 후보 근거 확인, 장소 선택과 정식 등록을 진행합니다." badge="검수" />
          <DashboardLink href="/admin/restaurants/new" title="맛집 등록" description="카카오 장소 URL을 검증한 뒤 맛집을 등록합니다." badge="등록" />
          <DashboardLink href="/admin/curations" title="큐레이션 관리" description="초안, 공개 상태와 맛집 구성을 관리합니다." badge="관리" />
          <DashboardLink href="/admin/participation" title="제보·신고 검토" description="접수된 요청을 검토하고 처리 상태를 기록합니다." badge="검토" />
        </div>
      </section>
    </AdminPage>
  )
}

function DashboardLink({ href, title, description, badge }: { href: string; title: string; description: string; badge: string }) {
  return <Link href={href} className={styles.dashboardCard}>
    <StatusBadge tone="info">{badge}</StatusBadge>
    <h2>{title}</h2>
    <p>{description}</p>
    <span aria-hidden="true">작업 열기 →</span>
  </Link>
}
