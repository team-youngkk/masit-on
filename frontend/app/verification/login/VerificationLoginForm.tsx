'use client'

import { FormEvent, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import {
  safeVerificationReturnTo,
  verificationLoginResult,
  verificationReturnToFromHash,
} from '@/lib/verification/login'

import styles from './page.module.css'

type VerificationLoginFormProps = {
  returnTo: string
}

export function VerificationLoginForm({ returnTo }: VerificationLoginFormProps) {
  const [message, setMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMessage(null)
    setSubmitting(true)

    const form = new FormData(event.currentTarget)

    try {
      const response = await fetch('/api/verification/sessions', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          loginId: String(form.get('loginId') ?? ''),
          password: String(form.get('password') ?? ''),
        }),
      })
      const result = verificationLoginResult(response.status)

      if (result.ok) {
        const hashReturnTo = verificationReturnToFromHash(window.location.hash)
        window.location.replace(safeVerificationReturnTo(hashReturnTo ?? returnTo))
        return
      }

      setMessage(result.message)
    } catch {
      setMessage('로그인 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      <Field
        label="검증 참여자 아이디"
        name="loginId"
        type="text"
        autoComplete="username"
        required
        disabled={submitting}
      />
      <Field
        label="비밀번호"
        name="password"
        type="password"
        autoComplete="current-password"
        required
        disabled={submitting}
      />
      {message ? (
        <p className={styles.error} role="alert">
          {message}
        </p>
      ) : null}
      <Button type="submit" disabled={submitting}>
        {submitting ? '확인 중…' : '입장하기'}
      </Button>
    </form>
  )
}
