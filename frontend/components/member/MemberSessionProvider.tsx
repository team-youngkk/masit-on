'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react'

import {
  ensureMemberSession,
  hasMemberAccessToken,
  memberLogout,
  MEMBER_SESSION_CHANGED_EVENT,
} from '@/lib/member/auth'

export type MemberSessionStatus = 'loading' | 'authenticated' | 'anonymous'

type MemberSessionContextValue = {
  status: MemberSessionStatus
  refreshSession: () => Promise<boolean>
  logout: () => Promise<void>
}

const MemberSessionContext = createContext<MemberSessionContextValue | null>(null)

export function MemberSessionProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<MemberSessionStatus>('loading')

  const refreshSession = useCallback(async () => {
    setStatus('loading')
    try {
      const authenticated = await ensureMemberSession()
      setStatus(authenticated ? 'authenticated' : 'anonymous')
      return authenticated
    } catch {
      setStatus('anonymous')
      return false
    }
  }, [])

  const logout = useCallback(async () => {
    try {
      await memberLogout()
    } finally {
      setStatus('anonymous')
    }
  }, [])

  useEffect(() => {
    const syncStatus = () => {
      setStatus(hasMemberAccessToken() ? 'authenticated' : 'anonymous')
    }

    window.addEventListener(MEMBER_SESSION_CHANGED_EVENT, syncStatus)
    void refreshSession()
    return () => window.removeEventListener(MEMBER_SESSION_CHANGED_EVENT, syncStatus)
  }, [refreshSession])

  const value = useMemo(
    () => ({ status, refreshSession, logout }),
    [status, refreshSession, logout],
  )

  return (
    <MemberSessionContext.Provider value={value}>
      {children}
    </MemberSessionContext.Provider>
  )
}

export function useMemberSession(): MemberSessionContextValue {
  const context = useContext(MemberSessionContext)
  if (!context) {
    throw new Error('useMemberSession must be used within MemberSessionProvider')
  }
  return context
}
