'use client'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useState } from 'react'

export function MapQueryProvider({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            /*
             * fetchMapPoints는 AbortError(취소, 재시도 대상 아님)를 제외한 네트워크·HTTP
             * 실패를 전부 잡아 정상 반환값({kind:'error', ...})으로 돌려주도록 설계돼 있어서,
             * useQuery 입장에서는 거의 항상 "성공"으로 끝난다. 이 retry는 fetchMapPoints가
             * 예기치 못하게 예외를 던지는 드문 경우에만 적용되는 사실상 죽은 설정이다.
             */
            retry: 1,
            refetchOnWindowFocus: false,
          },
        },
      }),
  )

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
