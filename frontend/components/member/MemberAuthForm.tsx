'use client'

import Link from 'next/link'
import { useId, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { cn } from '@/lib/cn'
import { memberLogin, memberRegister, requestPasswordReset, confirmPasswordReset, resendMemberEmailVerification } from '@/lib/member/auth'
import { memberVerifyEmailHref, safeAdminReturnTo, safeMemberReturnTo } from '@/lib/member/auth-navigation'
import {
  acceptMemberRegistration,
  type AcceptedMemberRegistration,
  type MemberAuthFieldErrors,
  type MemberAuthMode,
  prepareMemberAuthSubmission,
  resendAcceptedMemberRegistration,
  isInvalidMemberCredentialsResponse,
} from './member-auth-form-coordination'
import styles from './MemberAuthForm.module.css'

function getCurrentReturnTo(): string | null {
  const returnTo = new URLSearchParams(window.location.search).get('returnTo')
  return safeMemberReturnTo(returnTo)
}

function getSafeReturnTo(returnTo?: string | null): string {
  const candidate = returnTo ?? getCurrentReturnTo()
  return safeAdminReturnTo(candidate) ?? safeMemberReturnTo(candidate) ?? '/restaurants'
}

const CTA_LABELS: Record<MemberAuthMode, { idle: string; submitting: string }> = {
  login: { idle: '로그인', submitting: '로그인하는 중...' },
  signup: { idle: '가입하기', submitting: '가입 요청을 보내는 중...' },
  'request-reset': { idle: '재설정 메일 보내기', submitting: '재설정 메일을 요청하는 중...' },
  'confirm-reset': { idle: '비밀번호 변경', submitting: '비밀번호를 변경하는 중...' },
}

type AuthMessage = { tone: 'success' | 'danger'; text: string } | null

export function MemberAuthForm({ mode, returnTo }: { mode: MemberAuthMode; returnTo?: string | null }) {
  const router = useRouter()
  const passwordHintId = useId()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirmation, setPasswordConfirmation] = useState('')
  const [token, setToken] = useState('')
  const [message, setMessage] = useState<AuthMessage>(null)
  const [submitting, setSubmitting] = useState(false)
  const [registrationAccepted, setRegistrationAccepted] = useState(false)
  const [acceptedRegistration, setAcceptedRegistration] = useState<AcceptedMemberRegistration | null>(null)
  const [fieldErrors, setFieldErrors] = useState<MemberAuthFieldErrors>({})

  function clearFieldError(field: keyof MemberAuthFieldErrors) {
    setFieldErrors(current => current[field] ? { ...current, [field]: undefined } : current)
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMessage(null)
    const { errors, normalizedEmail } = prepareMemberAuthSubmission({
      mode,
      email,
      password,
      passwordConfirmation,
      token,
    })
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      setMessage({ tone: 'danger', text: '입력 내용을 확인해 주세요.' })
      return
    }

    setSubmitting(true)
    try {
      if (mode === 'login') { await memberLogin(normalizedEmail, password); router.replace(getSafeReturnTo(returnTo)) }
      if (mode === 'signup') {
        const submittedEmail = normalizedEmail
        await memberRegister(submittedEmail, password)
        setPassword('')
        setPasswordConfirmation('')
        setAcceptedRegistration(acceptMemberRegistration(submittedEmail))
        setRegistrationAccepted(true)
        setMessage({ tone: 'success', text: '가입 요청을 접수했습니다. 이메일에서 인증 안내를 확인해 주세요.' })
      }
      if (mode === 'request-reset') { await requestPasswordReset(normalizedEmail); setMessage({ tone: 'success', text: '비밀번호 재설정 요청을 접수했습니다. 이메일을 확인해 주세요.' }) }
      if (mode === 'confirm-reset') {
        await confirmPasswordReset(token, password)
        setPassword('')
        setPasswordConfirmation('')
        setMessage({ tone: 'success', text: '비밀번호를 변경하고 기존 로그인 세션을 종료했습니다. 새 비밀번호로 로그인해 주세요.' })
      }
    } catch (reason) {
      const invalidCredentials = isInvalidMemberCredentialsResponse(reason)
      if (mode === 'login') {
        setMessage({
          tone: 'danger',
          text: invalidCredentials ? '이메일 또는 비밀번호를 확인해 주세요.' : '로그인 요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.',
        })
      } else {
        setMessage({
          tone: 'danger',
          text: reason instanceof Response ? '요청을 완료하지 못했습니다. 입력 내용을 확인하고 다시 시도해 주세요.' : '요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.',
        })
      }
    } finally { setSubmitting(false) }
  }

  async function resendVerificationEmail() {
    if (!acceptedRegistration) {
      return
    }

    setMessage(null)
    setSubmitting(true)
    try {
      await resendAcceptedMemberRegistration(
        acceptedRegistration,
        resendMemberEmailVerification,
      )
      setMessage({ tone: 'success', text: '인증 메일 재발송 요청을 접수했습니다. 이메일을 확인해 주세요.' })
    } catch {
      setMessage({ tone: 'danger', text: '재발송 요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.' })
    } finally {
      setSubmitting(false)
    }
  }

  const needsEmail = mode !== 'confirm-reset'
  const showSignupInputs = !(mode === 'signup' && registrationAccepted)
  const needsPassword = (mode === 'login' || mode === 'confirm-reset') || (mode === 'signup' && showSignupInputs)
  const needsPasswordConfirmation = mode === 'confirm-reset' || (mode === 'signup' && showSignupInputs)
  const cta = CTA_LABELS[mode]
  return <form className={styles.form} onSubmit={submit} noValidate>
    {needsEmail ? <Field label="이메일" name="email" type="email" autoComplete="email" value={acceptedRegistration?.email ?? email} onChange={event => { setEmail(event.target.value); clearFieldError('email') }} readOnly={acceptedRegistration?.emailReadOnly ?? false} error={fieldErrors.email} required /> : null}
    {mode === 'confirm-reset' ? <Field label="비밀번호 재설정 토큰" name="token" value={token} onChange={event => { setToken(event.target.value); clearFieldError('token') }} error={fieldErrors.token} required /> : null}
    {needsPassword ? <>
      <Field label={mode === 'confirm-reset' ? '새 비밀번호' : '비밀번호'} name="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={event => { setPassword(event.target.value); clearFieldError('password') }} aria-describedby={mode === 'login' ? undefined : passwordHintId} error={fieldErrors.password} required />
      {mode !== 'login' ? <p id={passwordHintId} className={styles.hint}>비밀번호는 12자 이상 64자 이하로 입력해 주세요.</p> : null}
    </> : null}
    {needsPasswordConfirmation ? <Field label="비밀번호 확인" name="passwordConfirmation" type="password" autoComplete="new-password" value={passwordConfirmation} onChange={event => { setPasswordConfirmation(event.target.value); clearFieldError('passwordConfirmation') }} error={fieldErrors.passwordConfirmation} required /> : null}
    {message ? <p className={cn(styles.notice, message.tone === 'danger' && styles.noticeDanger)} role="alert">{message.text}</p> : null}
    {showSignupInputs || mode !== 'signup' ? <Button type="submit" disabled={submitting}>{submitting ? cta.submitting : cta.idle}</Button> : null}
    {mode === 'signup' && registrationAccepted ? (
      <Link className={styles.textLink} href={memberVerifyEmailHref(returnTo)}>이메일 인증 계속하기</Link>
    ) : null}
    {mode === 'signup' && registrationAccepted ? <Button type="button" variant="secondary" disabled={submitting} onClick={resendVerificationEmail}>{submitting ? '인증 메일을 다시 보내는 중...' : '인증 메일 다시 보내기'}</Button> : null}
  </form>
}
