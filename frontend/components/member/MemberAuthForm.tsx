'use client'

import Link from 'next/link'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { memberLogin, memberRegister, requestPasswordReset, confirmPasswordReset, resendMemberEmailVerification } from '@/lib/member/auth'
import { memberVerifyEmailHref, safeMemberReturnTo } from '@/lib/member/auth-navigation'
import styles from '@/components/admin/admin.module.css'

type Mode = 'login' | 'signup' | 'request-reset' | 'confirm-reset'

function getCurrentReturnTo(): string | null {
  const returnTo = new URLSearchParams(window.location.search).get('returnTo')
  return safeMemberReturnTo(returnTo)
}

function getSafeReturnTo(): string {
  return getCurrentReturnTo() ?? '/me'
}

export function MemberAuthForm({ mode, returnTo }: { mode: Mode; returnTo?: string | null }) {
  const router = useRouter()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirmation, setPasswordConfirmation] = useState('')
  const [token, setToken] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [registrationAccepted, setRegistrationAccepted] = useState(false)

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setMessage(null); setSubmitting(true)
    try {
      if ((mode === 'signup' || mode === 'confirm-reset') && password !== passwordConfirmation) {
        setMessage('Passwords do not match.')
        return
      }
      if (mode === 'login') { await memberLogin(email, password); router.replace(getSafeReturnTo()) }
      if (mode === 'signup') {
        await memberRegister(email, password)
        setPassword('')
        setPasswordConfirmation('')
        setRegistrationAccepted(true)
        setMessage('Check your email to verify the account.')
      }
      if (mode === 'request-reset') { await requestPasswordReset(email); setMessage('If the account exists, a reset email has been sent.') }
      if (mode === 'confirm-reset') {
        await confirmPasswordReset(token, password)
        setPassword('')
        setPasswordConfirmation('')
        setMessage('Password changed and existing sessions ended. You can now sign in.')
      }
    } catch (reason) {
      setMessage(reason instanceof Response ? 'Request could not be completed. Check the information and try again.' : 'Request could not be completed.')
    } finally { setSubmitting(false) }
  }

  async function resendVerificationEmail() {
    setMessage(null)
    setSubmitting(true)
    try {
      await resendMemberEmailVerification(email)
      setMessage('If the account is eligible, a verification email has been sent.')
    } catch {
      setMessage('Request could not be completed.')
    } finally {
      setSubmitting(false)
    }
  }

  const needsEmail = mode !== 'confirm-reset'
  const showSignupInputs = !(mode === 'signup' && registrationAccepted)
  const needsPassword = (mode === 'login' || mode === 'confirm-reset') || (mode === 'signup' && showSignupInputs)
  const needsPasswordConfirmation = mode === 'confirm-reset' || (mode === 'signup' && showSignupInputs)
  return <form className={styles.form} onSubmit={submit} noValidate>
    {needsEmail ? <Field label="Email" name="email" type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} required /> : null}
    {mode === 'confirm-reset' ? <Field label="Reset token" name="token" value={token} onChange={event => setToken(event.target.value)} required /> : null}
    {needsPassword ? <Field label="Password" name="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={event => setPassword(event.target.value)} required /> : null}
    {needsPasswordConfirmation ? <Field label="Confirm password" name="passwordConfirmation" type="password" autoComplete="new-password" value={passwordConfirmation} onChange={event => setPasswordConfirmation(event.target.value)} required /> : null}
    {message ? <p className={styles.error} role="alert">{message}</p> : null}
    {showSignupInputs || mode !== 'signup' ? <Button type="submit" disabled={submitting}>{submitting ? 'Working...' : 'Continue'}</Button> : null}
    {mode === 'signup' && registrationAccepted ? (
      <Link href={memberVerifyEmailHref(returnTo)}>Continue to email verification</Link>
    ) : null}
    {mode === 'signup' && registrationAccepted ? <Button type="button" variant="secondary" disabled={submitting} onClick={resendVerificationEmail}>Resend verification email</Button> : null}
  </form>
}
