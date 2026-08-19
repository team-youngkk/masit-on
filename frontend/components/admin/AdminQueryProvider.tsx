'use client'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useEffect, useState } from 'react'
import { useMemberSession } from '@/components/member/MemberSessionProvider'

export function AdminQueryProvider({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { retry: 1, refetchOnWindowFocus: false },
        },
      }),
  )

  return <AdminCacheBoundary queryClient={queryClient}>{children}</AdminCacheBoundary>
}

function AdminCacheBoundary({ children, queryClient }: { children: ReactNode; queryClient: QueryClient }) {
  const { session } = useMemberSession()
  useEffect(() => {
    if (session?.role !== 'ADMIN') queryClient.clear()
  }, [queryClient, session?.role])
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
