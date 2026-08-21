'use client'

import Link from 'next/link'
import { useEffect, useId, useRef, useState } from 'react'

import {
  resendMemberEmailVerification,
  verifyMemberEmail,
} from '@/lib/member/auth'
import {
  createEmailVerificationSingleFlight,
  normalizeEmailVerificationCodeInput,
  resendEmailVerification,
  submitEmailVerification,
} from '@/lib/member/email-verification-coordination'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import {
  MEMBER_EMAIL_VERIFICATION_TTL_SECONDS,
  PENDING_MEMBER_REGISTRATION_EMAIL_KEY,
  PENDING_MEMBER_REGISTRATION_REQUESTED_AT_KEY,
} from './member-auth-form-coordination'

import styles from './VerifyEmail.module.css'

type Feedback = {
  kind: 'status' | 'alert'
  text: string
} | null

function formatRemainingTime(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return [minutes, seconds].map(value => String(value).padStart(2, '0')).join(':')
}

export function VerifyEmail({ loginHref }: { loginHref: string }) {
  const [token, setToken] = useState('')
  const [email, setEmail] = useState('')
  const [verificationFeedback, setVerificationFeedback] = useState<Feedback>(null)
  const [resendFeedback, setResendFeedback] = useState<Feedback>(null)
  const [verifying, setVerifying] = useState(false)
  const [resending, setResending] = useState(false)
  const [verified, setVerified] = useState(false)
  const [showResend, setShowResend] = useState(true)
  const [emailLocked, setEmailLocked] = useState(false)
  const [verificationRequestedAt, setVerificationRequestedAt] = useState<number | null>(null)
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null)
  const tokenHintId = useId()
  const resendHintId = useId()
  const singleFlight = useRef(createEmailVerificationSingleFlight()).current
  const busy = verifying || resending
  const canCompleteSignup = token.length === 8

  useEffect(() => {
    try {
      const pendingEmail = window.sessionStorage.getItem(PENDING_MEMBER_REGISTRATION_EMAIL_KEY)
      if (pendingEmail) {
        setEmail(pendingEmail)
        setEmailLocked(true)
      }
      const requestedAt = Number(window.sessionStorage.getItem(PENDING_MEMBER_REGISTRATION_REQUESTED_AT_KEY))
      if (Number.isFinite(requestedAt) && requestedAt > 0) {
        setVerificationRequestedAt(requestedAt)
      }
    } catch {
      // 저장된 이메일이 없으면 인증 페이지에서 직접 입력할 수 있다.
    }
  }, [])

  useEffect(() => {
    if (verificationRequestedAt === null) {
      setRemainingSeconds(null)
      return
    }

    const expiresAt = verificationRequestedAt + MEMBER_EMAIL_VERIFICATION_TTL_SECONDS * 1000
    const updateRemainingTime = () => {
      setRemainingSeconds(Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000)))
    }

    updateRemainingTime()
    const intervalId = window.setInterval(updateRemainingTime, 1000)
    return () => window.clearInterval(intervalId)
  }, [verificationRequestedAt])

  async function submitVerification(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const submittedToken = normalizeEmailVerificationCodeInput(token)
    setToken(submittedToken)

    await singleFlight.run(async () => {
      setVerifying(true)
      setVerificationFeedback(null)
      setResendFeedback(null)
      setShowResend(false)

      try {
        const result = await submitEmailVerification({
          token: submittedToken,
          verify: verifyMemberEmail,
          clearToken: () => setToken(''),
        })

        setVerified(result.verified)
        setShowResend(result.shouldOfferResend)
        setVerificationFeedback(result.feedback)
        if (result.verified) {
          try {
            window.sessionStorage.removeItem(PENDING_MEMBER_REGISTRATION_EMAIL_KEY)
            window.sessionStorage.removeItem(PENDING_MEMBER_REGISTRATION_REQUESTED_AT_KEY)
          } catch {
            // 저장소 정리 실패는 인증 완료를 막지 않는다.
          }
        }
      } finally {
        setVerifying(false)
      }
    })
  }

  async function submitResend(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const submittedEmail = email

    await singleFlight.run(async () => {
      setResending(true)
      setResendFeedback(null)

      try {
        const result = await resendEmailVerification({
          email: submittedEmail,
          resend: resendMemberEmailVerification,
        })

        if (result.feedback.kind === 'status') {
          const requestedAt = Date.now()
          setVerificationRequestedAt(requestedAt)
          try {
            window.sessionStorage.setItem(PENDING_MEMBER_REGISTRATION_REQUESTED_AT_KEY, String(requestedAt))
          } catch {
            // 저장소가 차단되어도 현재 페이지에서는 타이머를 계속 표시한다.
          }
        }
        setResendFeedback(result.feedback)
      } finally {
        setResending(false)
      }
    })
  }

  return (
    <div className={styles.page}>
      <section className={styles.card} aria-labelledby="verify-email-title">
        <header className={styles.header}>
          <p className={styles.eyebrow}>회원가입</p>
          <h1 id="verify-email-title" className={styles.title}>
            이메일 인증
          </h1>
          <p className={styles.description}>
            이메일로 받은 8자 인증 코드를 입력해 가입을 완료하세요.
            <br />
            앞뒤 공백은 자동으로 제거되고 영문은 대문자로 입력됩니다.
          </p>
        </header>

        {!verified ? (
          <form className={styles.form} onSubmit={submitVerification}>
            <Field
              label="8자 인증 코드"
              name="token"
              value={token}
              onChange={event => {
                setToken(normalizeEmailVerificationCodeInput(event.target.value))
                if (verificationFeedback?.kind === 'alert') {
                  setVerificationFeedback(null)
                  setShowResend(false)
                }
              }}
              aria-describedby={tokenHintId}
              autoComplete="one-time-code"
              autoCapitalize="characters"
              inputMode="text"
              placeholder="AB7K9M2Q"
              spellCheck={false}
              disabled={busy}
              required
            />
            <p id={tokenHintId} className={styles.help}>
              메일 본문에 포함된 8자 코드를 입력해 주세요.
            </p>
            <p
              className={remainingSeconds === 0 ? styles.alert : styles.timer}
              role={remainingSeconds === null ? undefined : 'timer'}
            >
              {remainingSeconds === null
                ? `인증 코드는 발급 후 ${Math.floor(MEMBER_EMAIL_VERIFICATION_TTL_SECONDS / 60)}분 동안 유효합니다.`
                : remainingSeconds > 0
                  ? `인증 코드 남은 시간 ${formatRemainingTime(remainingSeconds)}`
                  : '인증 코드가 만료되었습니다. 인증 메일을 다시 요청해 주세요.'}
            </p>

            {verificationFeedback ? (
              <p
                className={
                  verificationFeedback.kind === 'alert'
                    ? styles.alert
                    : styles.status
                }
                role={verificationFeedback.kind}
              >
                {verificationFeedback.text}
              </p>
            ) : (
              <p className={styles.notice}>
                인증이 완료되면 자동 로그인되지 않으며, 로그인 화면에서 직접
                로그인해야 합니다.
              </p>
            )}

            {canCompleteSignup ? <Button type="submit" disabled={busy}>
              {verifying ? '인증 중...' : '가입 완료'}
            </Button> : null}
          </form>
        ) : (
          <div className={styles.successPanel}>
            {verificationFeedback ? (
              <p className={styles.status} role="status">
                {verificationFeedback.text}
              </p>
            ) : null}
            <Link className={styles.loginLink} href={loginHref}>
              로그인 화면으로 이동
            </Link>
          </div>
        )}
      </section>

      {showResend ? (
        <section className={styles.card} aria-labelledby="resend-title">
          <div className={styles.sectionHeader}>
            <h2 id="resend-title" className={styles.sectionTitle}>
              인증 메일 다시 요청
            </h2>
          </div>

          <form className={styles.form} onSubmit={submitResend}>
            <Field
              label="이메일"
              name="email"
              type="email"
              value={email}
              readOnly={emailLocked}
              onChange={event => {
                setEmail(event.target.value)
                if (resendFeedback) {
                  setResendFeedback(null)
                }
              }}
              aria-describedby={resendHintId}
              autoComplete="email"
              disabled={busy}
              required
            />
            <div id={resendHintId} className={styles.helpList}>
              <p className={styles.help}>
                재발송은 최소 60초 간격으로 요청할 수 있습니다.
              </p>
              <p className={styles.help}>
                하루 최대 5회까지 요청할 수 있습니다.
              </p>
            </div>

            {resendFeedback ? (
              <p
                className={
                  resendFeedback.kind === 'alert' ? styles.alert : styles.status
                }
                role={resendFeedback.kind}
              >
                {resendFeedback.text}
              </p>
            ) : null}

            <Button type="submit" variant="secondary" disabled={busy}>
              {resending ? '요청 중...' : '인증 메일 다시 요청'}
            </Button>
          </form>
        </section>
      ) : null}
    </div>
  )
}

