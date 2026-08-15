'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import { PageShell } from '@/components/ui/PageShell'
import { StatePanel } from '@/components/ui/StatePanel'
import { authenticatedMemberFetch, clearMemberAccessToken, memberLogout } from '@/lib/member/auth'
import styles from './page.module.css'

type Me = { id: string; email: string }
type Action = 'logout' | 'withdraw' | null

export default function MePage() {
  const { status } = useMemberSession()
  const [member, setMember] = useState<Me | null>(null)
  const [message, setMessage] = useState('Loading account...')
  const [messageIsError, setMessageIsError] = useState(false)
  const [action, setAction] = useState<Action>(null)
  const [confirmingWithdrawal, setConfirmingWithdrawal] = useState(false)

  useEffect(() => {
    let active = true
    if (status !== 'authenticated') {
      setMember(null)
      setMessage(status === 'loading' ? 'Loading account...' : 'Sign in is required.')
      setMessageIsError(false)
      return () => { active = false }
    }

    authenticatedMemberFetch('/api/me')
      .then(async response => {
        if (!active) return
        if (!response.ok) {
          setMessageIsError(response.status !== 401)
          setMessage(response.status === 401 ? 'Sign in is required.' : 'Could not load your account.')
          return
        }
        const nextMember = (await response.json()) as Me
        if (!active) return
        setMember(nextMember)
        setMessage('')
      })
      .catch(() => {
        if (!active) return
        setMessageIsError(true)
        setMessage('Could not load your account.')
      })
    return () => { active = false }
  }, [status])

  async function logout() {
    setAction('logout')
    setMessageIsError(false)
    setMessage('Signing out...')
    try {
      await memberLogout()
      setMember(null)
      setMessage('You have been signed out.')
    } catch (reason) {
      setMember(null)
      if (reason instanceof Response && reason.status === 401) {
        setMessageIsError(false)
        setMessage('Your session has expired. This device is signed out; sign in again to continue.')
      } else {
        setMessageIsError(true)
        setMessage('Logout could not be completed. This device is signed out, but the server session could not be confirmed. Please try again after recovery.')
      }
    } finally {
      setAction(null)
    }
  }

  async function withdraw() {
    setAction('withdraw')
    setMessageIsError(false)
    setMessage('Starting account deletion...')
    try {
      const response = await authenticatedMemberFetch('/api/me', { method: 'DELETE' })
      if (response.status !== 202) {
        throw response
      }
      clearMemberAccessToken()
      setMember(null)
      setConfirmingWithdrawal(false)
      setMessage('Account deletion is in progress. Sign-in and re-registration with this email remain blocked until cleanup finishes.')
    } catch (reason) {
      setMessageIsError(true)
      setMessage(reason instanceof Response && reason.status === 401
        ? 'Your session has expired. Sign in again before requesting account deletion.'
        : 'Account deletion could not be started. Your account remains protected; please try again after recovery.')
    } finally {
      setAction(null)
    }
  }

  const destinations = [
    ['찜한 맛집', '/me/favorites', '저장한 맛집을 확인합니다.'],
    ['최근 본 맛집', '/me/recent-restaurants', '최근 확인한 맛집을 다시 봅니다.'],
    ['내 컬렉션', '/me/collections', '목적별로 맛집을 모아 봅니다.'],
    ['알림', '/me/notifications', '제보·신고 처리 알림을 확인합니다.'],
    ['제보·신고', '/me/requests', '새 정보 제안과 기존 정보 신고를 관리합니다.'],
  ] as const

  return <PageShell size="narrow" eyebrow="회원" title="내 정보" description="내 계정과 개인화 메뉴를 관리합니다.">
    {member ? <>
      <section className={styles.summary} aria-label="현재 로그인한 계정">
        <strong>{member.email}</strong>
        <Button variant="secondary" disabled={action !== null} onClick={logout}>{action === 'logout' ? '로그아웃 중…' : '로그아웃'}</Button>
      </section>
      <nav className={styles.menu} aria-label="내 정보 메뉴">
        {destinations.map(([label, href, description]) => <Link key={href} href={href}><strong>{label}</strong><span>{description}</span><span aria-hidden="true">›</span></Link>)}
      </nav>
      {!confirmingWithdrawal
        ? <Button variant="secondary" disabled={action !== null} onClick={() => { setConfirmingWithdrawal(true); setMessage('') }}>회원 탈퇴</Button>
        : <div role="group" aria-labelledby="withdrawal-confirmation">
          <h2 id="withdrawal-confirmation">회원 탈퇴를 진행할까요?</h2>
          <p>계정 정보, 찜, 최근 본 기록과 모든 로그인 세션이 정리됩니다.</p>
          <Button variant="secondary" disabled={action !== null} onClick={() => { setConfirmingWithdrawal(false); setMessage('') }}>취소</Button>
          <Button disabled={action !== null} onClick={withdraw}>
            {action === 'withdraw' ? '처리 시작 중…' : '탈퇴 확인'}
          </Button>
        </div>}
    </> : null}
    {message ? <StatePanel compact tone={messageIsError ? 'danger' : 'neutral'} title={message} /> : null}
  </PageShell>
}
