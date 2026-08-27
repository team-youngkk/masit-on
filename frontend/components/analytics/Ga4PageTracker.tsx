'use client'

import { usePathname, useSearchParams } from 'next/navigation'
import { useEffect, useRef } from 'react'

import { trackPageView } from '@/lib/analytics'

export function Ga4PageTracker() {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const navigationKey = pathname ? `${pathname}?${searchParams.toString()}` : ''
  const trackedNavigationKey = useRef<string | null>(null)

  useEffect(() => {
    if (!pathname || trackedNavigationKey.current === navigationKey) {
      return
    }

    if (trackPageView(pathname)) {
      trackedNavigationKey.current = navigationKey
    }
  }, [navigationKey, pathname])

  return null
}
