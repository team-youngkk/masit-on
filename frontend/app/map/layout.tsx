import type { ReactNode } from 'react'

import { MapQueryProvider } from '@/components/map/MapQueryProvider'

export default function MapLayout({ children }: { children: ReactNode }) {
  return <MapQueryProvider>{children}</MapQueryProvider>
}
