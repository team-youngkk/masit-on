'use client'

import { useEffect, useState } from 'react'
import { verifyMemberEmail } from '@/lib/member/auth'

export function VerifyEmail({ token }: { token?: string }) {
  const [message, setMessage] = useState('Verifying email...')
  useEffect(() => {
    if (!token) { setMessage('Verification token is missing.'); return }
    verifyMemberEmail(token).then(() => setMessage('Email verified. You can now sign in.')).catch(() => setMessage('This verification link is invalid or expired.'))
  }, [token])
  return <p>{message}</p>
}
