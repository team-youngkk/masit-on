'use client'

import { QueryClient, QueryClientProvider, useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { MEMBER_SESSION_CHANGED_EVENT, memberLogout, restoreMemberSessionOnce, type MemberSession } from '@/lib/member/auth'

export type MemberSessionStatus = 'loading' | 'authenticated' | 'anonymous' | 'unavailable'
type MemberSessionContextValue = { status: MemberSessionStatus; session: MemberSession | null; refreshSession: () => Promise<boolean>; logout: () => Promise<void> }
const MemberSessionContext = createContext<MemberSessionContextValue | null>(null)
export const AUTH_SESSION_QUERY_KEY = ['auth', 'session'] as const

function SessionState({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<MemberSessionStatus>('loading')
  const { data: session = null } = useQuery<MemberSession | null>({
    queryKey: AUTH_SESSION_QUERY_KEY,
    queryFn: () => null,
    enabled: false,
  })
  const apply = useCallback((next: MemberSession | null, nextStatus: MemberSessionStatus) => {
    setStatus(nextStatus)
    if (next) queryClient.setQueryData(AUTH_SESSION_QUERY_KEY, next)
    else { queryClient.removeQueries({ queryKey: AUTH_SESSION_QUERY_KEY }); queryClient.removeQueries({ predicate: query => query.queryKey[0] === 'auth' }) }
  }, [queryClient])
  const refreshSession = useCallback(async () => {
    setStatus('loading')
    const restored = await restoreMemberSessionOnce()
    if (restored.unavailable) { apply(null, 'unavailable'); return false }
    apply(restored.session, restored.session ? 'authenticated' : 'anonymous')
    return restored.session !== null
  }, [apply])
  const logout = useCallback(async () => { try { await memberLogout() } finally { apply(null, 'anonymous') } }, [apply])
  useEffect(() => { const sync = () => { void refreshSession() }; window.addEventListener(MEMBER_SESSION_CHANGED_EVENT, sync); void refreshSession(); return () => window.removeEventListener(MEMBER_SESSION_CHANGED_EVENT, sync) }, [refreshSession])
  const value = useMemo(() => ({ status, session, refreshSession, logout }), [status, session, refreshSession, logout])
  return <MemberSessionContext.Provider value={value}>{children}</MemberSessionContext.Provider>
}
export function MemberSessionProvider({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } } }))
  return <QueryClientProvider client={queryClient}><SessionState>{children}</SessionState></QueryClientProvider>
}
export function useMemberSession(): MemberSessionContextValue { const context = useContext(MemberSessionContext); if (!context) throw new Error('useMemberSession must be used within MemberSessionProvider'); return context }
