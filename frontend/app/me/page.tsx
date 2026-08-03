'use client'

import { useEffect, useState } from 'react'
import { useMemberSession } from '@/components/member/MemberSessionProvider'
import { Button } from '@/components/ui/Button'
import { authenticatedMemberFetch, clearMemberAccessToken, memberLogout } from '@/lib/member/auth'

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

  return <section>
    <h1>My account</h1>
    {member ? <>
      <p>{member.email}</p>
      <Button variant="secondary" disabled={action !== null} onClick={logout}>
        {action === 'logout' ? 'Signing out...' : 'Sign out'}
      </Button>
      {!confirmingWithdrawal
        ? <Button variant="secondary" disabled={action !== null} onClick={() => { setConfirmingWithdrawal(true); setMessage('') }}>Delete account</Button>
        : <div role="group" aria-labelledby="withdrawal-confirmation">
          <h2 id="withdrawal-confirmation">Confirm account deletion</h2>
          <p>Your account information, favorites, recent history, and all authentication sessions will be deleted.</p>
          <Button variant="secondary" disabled={action !== null} onClick={() => { setConfirmingWithdrawal(false); setMessage('') }}>Cancel</Button>
          <Button disabled={action !== null} onClick={withdraw}>
            {action === 'withdraw' ? 'Starting deletion...' : 'Confirm deletion'}
          </Button>
        </div>}
    </> : null}
    {message ? <p role={messageIsError ? 'alert' : 'status'}>{message}</p> : null}
  </section>
}
