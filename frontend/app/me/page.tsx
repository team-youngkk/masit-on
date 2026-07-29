'use client'

import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/Button'
import { authenticatedMemberFetch, clearMemberAccessToken } from '@/lib/member/auth'

type Me = { email: string; status: string }
export default function MePage() {
  const [member, setMember] = useState<Me | null>(null)
  const [message, setMessage] = useState('Loading account...')
  useEffect(() => { authenticatedMemberFetch('/api/me').then(async response => { if (!response.ok) { setMessage('Sign in is required.'); return }; setMember(await response.json()); setMessage('') }).catch(() => setMessage('Could not load your account.')) }, [])
  async function withdraw() {
    const response = await authenticatedMemberFetch('/api/me', { method: 'DELETE' })
    if (response.ok) {
      clearMemberAccessToken()
      setMember(null)
      setMessage('Deletion has been requested. You have been signed out.')
      return
    }
    setMessage('Could not request deletion.')
  }
  return <section><h1>My account</h1>{member ? <><p>{member.email}</p><p>{member.status}</p><Button variant="secondary" onClick={withdraw}>Delete account</Button></> : <p>{message}</p>}</section>
}
