import type { Metadata, Viewport } from 'next'

import { GoogleAnalytics } from '@/components/analytics/GoogleAnalytics'
import { AppFrame } from '@/components/layout/AppFrame'
import { MemberSessionProvider } from '@/components/member/MemberSessionProvider'
import { getSiteUrl } from '@/lib/site-url'
import { themeInitScript } from '@/lib/theme'

import './globals.css'
import styles from './layout.module.css'

export const metadata: Metadata = {
  metadataBase: getSiteUrl() ?? undefined,
  title: '맛잇온',
  description: '유튜버가 방문한 맛집을 지역·음식 종류·유튜버로 탐색하는 서비스',
  robots: { index: false, follow: false },
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
    <html lang="ko" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body className={styles.shell}>
        <MemberSessionProvider>
          <AppFrame>{children}</AppFrame>
        </MemberSessionProvider>
        <GoogleAnalytics measurementId={process.env.NEXT_PUBLIC_GA_MEASUREMENT_ID} />
      </body>
    </html>
  )
}
