'use client'

import { useEffect, useRef } from 'react'

import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { authenticatedMemberFetch } from '@/lib/member/auth'

export function RecentViewRecorder({ restaurantId }: { restaurantId: string }) {
  const { status } = useMemberSession()
  const requestedRestaurantId = useRef<string | null>(null)

  useEffect(() => {
    if (status !== 'authenticated') {
      requestedRestaurantId.current = null
      return
    }
    if (requestedRestaurantId.current === restaurantId) return
    requestedRestaurantId.current = restaurantId

    // 공개 상세 표시는 이 선택적 회원 기록 요청의 성공 여부와 분리한다.
    void authenticatedMemberFetch(
      `/api/restaurants/${encodeURIComponent(restaurantId)}`,
    ).catch(() => undefined)
  }, [restaurantId, status])

  return null
}
