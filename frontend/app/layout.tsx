import type { Metadata, Viewport } from 'next'

import { SiteFooter } from '@/components/layout/SiteFooter'
import { SiteHeader } from '@/components/layout/SiteHeader'
import { MemberSessionProvider } from '@/components/member/MemberSessionProvider'

import './globals.css'
import styles from './layout.module.css'

export const metadata: Metadata = {
  title: '맛잇온',
  description: '유튜버가 방문한 맛집을 지역·음식 종류·유튜버로 탐색하는 서비스',
}

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="ko">
      <body className={styles.shell}>
        <MemberSessionProvider>
          <SiteHeader />
          <main className={styles.main}>{children}</main>
          <SiteFooter />
        </MemberSessionProvider>
      </body>
    </html>
  )
}
