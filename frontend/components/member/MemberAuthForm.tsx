'use client'

import { useEffect, useId, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { cn } from '@/lib/cn'
import { memberLogin, memberRegister, requestPasswordReset, confirmPasswordReset } from '@/lib/member/auth'
import { memberLoginDestination, memberLoginHref, memberVerifyEmailHref, safeMemberReturnTo } from '@/lib/member/auth-navigation'
import {
  type MemberAuthFieldErrors,
  type MemberAuthMode,
  PENDING_MEMBER_REGISTRATION_EMAIL_KEY,
  PENDING_MEMBER_REGISTRATION_REQUESTED_AT_KEY,
  PASSWORD_POLICY_ERROR_MESSAGE,
  prepareMemberAuthSubmission,
  isInvalidMemberCredentialsResponse,
  watchPasswordResetToken,
} from './member-auth-form-coordination'
import styles from './MemberAuthForm.module.css'

function getCurrentReturnTo(): string | null {
  const returnTo = new URLSearchParams(window.location.search).get('returnTo')
  return safeMemberReturnTo(returnTo)
}

function getSafeReturnTo(returnTo?: string | null): string {
  const candidate = returnTo ?? getCurrentReturnTo()
  return memberLoginDestination(candidate)
}

const CTA_LABELS: Record<MemberAuthMode, { idle: string; submitting: string }> = {
  login: { idle: '로그인', submitting: '로그인하는 중...' },
  signup: { idle: '인증 요청', submitting: '인증 요청을 보내는 중...' },
  'request-reset': { idle: '재설정 메일 보내기', submitting: '재설정 메일을 요청하는 중...' },
  'confirm-reset': { idle: '비밀번호 변경', submitting: '비밀번호를 변경하는 중...' },
}

type AuthMessage = { tone: 'success' | 'danger'; text: string } | null

export function MemberAuthForm({ mode, returnTo }: { mode: MemberAuthMode; returnTo?: string | null }) {
  const router = useRouter()
  const passwordRulesId = useId()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirmation, setPasswordConfirmation] = useState('')
  const [token, setToken] = useState('')
  const [message, setMessage] = useState<AuthMessage>(null)
  const [submitting, setSubmitting] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<MemberAuthFieldErrors>({})

  useEffect(() => {
    if (mode !== 'confirm-reset') {
      return
    }

    return watchPasswordResetToken(
      () => window.location.hash,
      resetToken => setToken(resetToken),
      () => window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`),
      listener => {
        window.addEventListener('hashchange', listener)
        return () => window.removeEventListener('hashchange', listener)
      },
    )
  }, [mode])

  function clearFieldError(field: keyof MemberAuthFieldErrors) {
    setFieldErrors(current => current[field] ? { ...current, [field]: undefined } : current)
  }

  async function requestSignupVerification() {
    setMessage(null)
    const { errors, normalizedEmail } = prepareMemberAuthSubmission({
      mode: 'signup',
      email,
      password,
      passwordConfirmation,
      token: '',
    })
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      setMessage({ tone: 'danger', text: '입력 내용을 확인해 주세요.' })
      return
    }

    setSubmitting(true)
    try {
      await memberRegister(normalizedEmail, password)
      try {
        window.sessionStorage.setItem(PENDING_MEMBER_REGISTRATION_EMAIL_KEY, normalizedEmail)
        window.sessionStorage.setItem(PENDING_MEMBER_REGISTRATION_REQUESTED_AT_KEY, String(Date.now()))
      } catch {
        try {
          window.sessionStorage.removeItem(PENDING_MEMBER_REGISTRATION_EMAIL_KEY)
          window.sessionStorage.removeItem(PENDING_MEMBER_REGISTRATION_REQUESTED_AT_KEY)
        } catch {
          // 저장소 접근이 차단된 경우 인증 페이지에서 이메일을 다시 입력한다.
        }
      }
      router.push(memberVerifyEmailHref(returnTo))
    } catch (reason) {
      setMessage({
        tone: 'danger',
        text: reason instanceof Response ? '인증 요청을 완료하지 못했습니다. 입력 내용을 확인하고 다시 시도해 주세요.' : '인증 요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      })
    } finally { setSubmitting(false) }
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMessage(null)
    if (mode === 'signup') {
      await requestSignupVerification()
      return
    }

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

  const needsEmail = mode !== 'confirm-reset'
  const needsPassword = mode === 'login' || mode === 'signup' || mode === 'confirm-reset'
  const needsPasswordConfirmation = mode === 'confirm-reset' || mode === 'signup'
  const showPasswordRules = mode === 'signup' || mode === 'confirm-reset'
  const hasPasswordInput = password.length > 0
  const passwordRules = [
    { label: '12자 이상', valid: hasPasswordInput && password.length >= 12 },
    { label: '64자 이하', valid: hasPasswordInput && password.length <= 64 },
  ]
  const passwordMatch = passwordConfirmation.length > 0 && password.length > 0
    ? password === passwordConfirmation
    : null
  const cta = CTA_LABELS[mode]
  const canRequestSignup = email.trim().length > 0 && password.length > 0 && passwordConfirmation.length > 0
  const passwordDescribedBy = mode === 'login' ? undefined : passwordRulesId
  const passwordError = fieldErrors.password === PASSWORD_POLICY_ERROR_MESSAGE ? undefined : fieldErrors.password
  return <form className={styles.form} onSubmit={submit} noValidate>
    {needsEmail ? <Field label="이메일" name="email" type="email" autoComplete="email" value={email} onChange={event => { setEmail(event.target.value); clearFieldError('email') }} error={fieldErrors.email} required /> : null}
    {needsPassword ? <>
      <Field label={mode === 'confirm-reset' ? '새 비밀번호' : '비밀번호'} name="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={event => { const nextPassword = event.target.value; setPassword(nextPassword); clearFieldError('password'); if (nextPassword === passwordConfirmation) clearFieldError('passwordConfirmation') }} aria-describedby={passwordDescribedBy} aria-invalid={fieldErrors.password ? true : undefined} error={passwordError} required />
      {mode !== 'login' ? <>
        {showPasswordRules ? <ul id={passwordRulesId} className={styles.passwordRules} aria-label="비밀번호 조건">
          {passwordRules.map((rule) => <li key={rule.label} className={rule.valid ? styles.passwordRuleValid : undefined}>
            <span aria-hidden="true">{rule.valid ? '✓' : '○'}</span><span>{rule.label} {rule.valid ? '충족' : '미충족'}</span>
          </li>)}
        </ul> : null}
      </> : null}
    </> : null}
    {needsPasswordConfirmation ? <div className={styles.passwordConfirmationField}>
      <Field label="비밀번호 확인" name="passwordConfirmation" type="password" autoComplete="new-password" value={passwordConfirmation} onChange={event => { setPasswordConfirmation(event.target.value); clearFieldError('passwordConfirmation') }} error={passwordMatch === false ? undefined : fieldErrors.passwordConfirmation} required />
      {passwordMatch !== null ? <p className={cn(styles.passwordMatch, passwordMatch ? styles.passwordMatchValid : styles.passwordMatchInvalid)} role="status" aria-live="polite">
        {passwordMatch ? '비밀번호가 일치합니다.' : '비밀번호가 일치하지 않습니다.'}
      </p> : null}
    </div> : null}
    {mode === 'confirm-reset' && fieldErrors.token ? <p className={styles.hint} role="alert">재설정 메일의 링크를 통해 다시 접속해 주세요.</p> : null}
    {message ? <p className={cn(styles.notice, message.tone === 'danger' && styles.noticeDanger)} role="alert">{message.text}</p> : null}
    {mode !== 'signup' || canRequestSignup ? <Button type="submit" disabled={submitting}>{submitting ? cta.submitting : cta.idle}</Button> : null}
  </form>
}
