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
          description="동네 취재 노트의 입력·검증·검토·완료 작업을 한 화면에서 이어갑니다."
        />
        <section className={styles.dashboardProcess} aria-labelledby="admin-process-heading">
          <div className={styles.dashboardProcessHeader}>
            <p className={styles.eyebrow}>작업 순서</p>
            <h3 id="admin-process-heading">입력한 근거가 공개 기록이 되기까지</h3>
            <p>각 작업 화면에서 현재 상태와 다음 확인 지점을 먼저 읽을 수 있습니다.</p>
          </div>
          <ol className={styles.dashboardSteps}>
            <li><span className={styles.dashboardStepMarker} aria-hidden="true" /><span><strong>입력</strong><small>원본 정보 작성</small></span></li>
            <li><span className={styles.dashboardStepMarker} aria-hidden="true" /><span><strong>검증</strong><small>외부 근거 확인</small></span></li>
            <li><span className={styles.dashboardStepMarker} aria-hidden="true" /><span><strong>검토</strong><small>중복·예외 확인</small></span></li>
            <li><span className={styles.dashboardStepMarker} aria-hidden="true" /><span><strong>완료</strong><small>등록 결과 기록</small></span></li>
          </ol>
        </section>
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
  return (
    <Link href={href} className={styles.dashboardCard}>
      <div className={styles.dashboardCardTop}>
        <StatusBadge tone="info">{badge}</StatusBadge>
        <span className={styles.dashboardCardPath}>{href}</span>
      </div>
      <h2>{title}</h2>
      <p>{description}</p>
      <span className={styles.dashboardCardAction}>작업 열기</span>
    </Link>
  )
}
