'use client'

import Link from 'next/link'
import { useId, useRef, useState } from 'react'

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

import styles from './VerifyEmail.module.css'

type Feedback = {
  kind: 'status' | 'alert'
  text: string
} | null

export function VerifyEmail({ loginHref }: { loginHref: string }) {
  const [token, setToken] = useState('')
  const [email, setEmail] = useState('')
  const [verificationFeedback, setVerificationFeedback] = useState<Feedback>(null)
  const [resendFeedback, setResendFeedback] = useState<Feedback>(null)
  const [verifying, setVerifying] = useState(false)
  const [resending, setResending] = useState(false)
  const [verified, setVerified] = useState(false)
  const [showResend, setShowResend] = useState(false)
  const tokenHintId = useId()
  const resendHintId = useId()
  const singleFlight = useRef(createEmailVerificationSingleFlight()).current
  const busy = verifying || resending

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
          <p className={styles.eyebrow}>회원가입 확인</p>
          <h1 id="verify-email-title" className={styles.title}>
            이메일 인증
          </h1>
          <p className={styles.description}>
            이메일로 받은 8자 인증 코드를 입력해 가입을 완료하세요. 앞뒤 공백은
            자동으로 제거되고 영문은 대문자로 입력됩니다.
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

            <Button type="submit" disabled={busy}>
              {verifying ? '인증 중...' : '이메일 인증'}
            </Button>
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
            <p className={styles.description}>
              계정 상태나 실제 발송 여부는 안내하지 않습니다. 재발송 제한은
              화면에서 계산하지 않고 아래 기준만 안내합니다.
            </p>
          </div>

          <form className={styles.form} onSubmit={submitResend}>
            <Field
              label="이메일"
              name="email"
              type="email"
              value={email}
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
