'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'

import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { fieldErrorsFor, messageFor } from '@/lib/admin/api'
import { login } from '@/lib/admin/auth'

import styles from './admin.module.css'

type LoginErrorBody = {
  message?: string
  errors?: Array<{ field: string; reason: string }>
  traceId?: string
}

async function loginErrorFor(reason: unknown): Promise<{
  message: string
  fieldErrors: Record<string, string>
}> {
  if (!(reason instanceof Response)) {
    return { message: messageFor(reason), fieldErrors: fieldErrorsFor(reason) }
  }

  try {
    const body = (await reason.json()) as LoginErrorBody
    if (reason.status >= 500) {
      return {
        message: body.traceId
          ? `로그인을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요. (문의 ID: ${body.traceId})`
          : '로그인을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
        fieldErrors: {},
      }
    }
    return {
      message: body.message ?? '로그인에 실패했습니다. 입력값을 확인해 주세요.',
      fieldErrors: Object.fromEntries(
        (body.errors ?? []).map(({ field, reason: fieldReason }) => [field, fieldReason]),
      ),
    }
  } catch {
    return { message: '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.', fieldErrors: {} }
  }
}

export function LoginForm() {
  const router = useRouter()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)

    try {
      await login(loginId.trim(), password)
      router.replace('/admin/restaurants/new')
    } catch (reason) {
      const loginError = await loginErrorFor(reason)
      setFieldErrors(loginError.fieldErrors)
      setError(loginError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <Field
        label="로그인 ID"
        name="loginId"
        autoComplete="username"
        value={loginId}
        onChange={(event) => setLoginId(event.target.value)}
        error={fieldErrors.loginId}
        required
      />
      <Field
        label="비밀번호"
        name="password"
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        error={fieldErrors.password}
        required
      />
      {error ? <p className={styles.error} role="alert">{error}</p> : null}
      <Button type="submit" disabled={submitting}>
        {submitting ? '로그인 중…' : '로그인'}
      </Button>
    </form>
  )
}
