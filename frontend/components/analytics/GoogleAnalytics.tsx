import Script from 'next/script'
import { Suspense } from 'react'

import { isValidGa4MeasurementId } from '@/lib/analytics'

import { Ga4PageTracker } from './Ga4PageTracker'

type Props = {
  measurementId: string | undefined
}

export function GoogleAnalytics({ measurementId }: Props) {
  if (!isValidGa4MeasurementId(measurementId)) {
    return null
  }

  const id = measurementId.trim()

  return (
    <>
      <Script
        async
        src={`https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(id)}`}
        strategy="afterInteractive"
      />
      <Script id="masiton-ga4" strategy="afterInteractive">
        {`
          window.dataLayer = window.dataLayer || [];
          window.gtag = window.gtag || function(){window.dataLayer.push(arguments);};
          window.gtag('js', new Date());
          window.gtag('config', ${JSON.stringify(id)}, {
            send_page_view: false,
            allow_google_signals: false,
            allow_ad_personalization_signals: false
          });
        `}
      </Script>
      <Suspense fallback={null}>
        <Ga4PageTracker />
      </Suspense>
    </>
  )
}
