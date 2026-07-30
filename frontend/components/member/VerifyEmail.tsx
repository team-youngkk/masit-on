'use client'

import { useState } from 'react'
import { verifyMemberEmail } from '@/lib/member/auth'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'

export function VerifyEmail() {
  const [token, setToken] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setMessage(null)
    try {
      await verifyMemberEmail(token)
      setMessage('Email verified. You can now sign in.')
    } catch {
      setMessage('This token is invalid or expired.')
    } finally {
      setSubmitting(false)
    }
  }

  return <form onSubmit={submit}><Field label="Verification token" name="token" value={token} onChange={event => setToken(event.target.value)} required />{message ? <p role="alert">{message}</p> : null}<Button type="submit" disabled={submitting}>{submitting ? 'Verifying...' : 'Verify email'}</Button></form>
}
