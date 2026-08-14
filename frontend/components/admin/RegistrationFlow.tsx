'use client'

import { useId, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'

import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { adminJson, fieldErrorsFor, messageFor } from '@/lib/admin/api'
import { registrationStepDecision } from '@/lib/admin/registration-progression'

import styles from './admin.module.css'

type Input = {
  name: string
  label: string
  type?: 'text' | 'url' | 'tel'
  required?: boolean
  /* 지정하면 자유 입력 대신 select로 렌더링한다. */
  options?: string[]
}

type Preview = {
  decision: 'READY' | 'DUPLICATE' | 'REVIEW_REQUIRED'
  confirmationToken: string | null
  expiresAt: string | null
  candidate: Record<string, unknown> | null
  existingResource: Record<string, unknown> | null
}

type RegistrationFlowProps = {
  inputs: Input[]
  previewPath: string
  createPath: string
  resourceName: string
  /* 넘기지 않으면 빈 값에서 시작하는 기존 동작과 같다. */
  initialValues?: Record<string, string>
  /* READY 확정 생성 또는 DUPLICATE 판정으로 자원 id가 정해지면 호출한다. */
  onCompleted?: (resourceId: string, kind: 'created' | 'duplicate') => void
}

function formatRecord(record: Record<string, unknown> | null): string | null {
  if (!record) {
    return null
  }

  return Object.entries(record)
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => `${key}: ${String(value)}`)
    .join('\n')
}

export function RegistrationFlow({
  inputs,
  previewPath,
  createPath,
  resourceName,
  initialValues,
  onCompleted,
}: RegistrationFlowProps) {
  const queryClient = useQueryClient()
  const fieldIdPrefix = useId()
  const [values, setValues] = useState<Record<string, string>>(() => initialValues ?? {})
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [preview, setPreview] = useState<Preview | null>(null)
  const [created, setCreated] = useState<Record<string, unknown> | null>(null)
  const previewGeneration = useRef(0)
  const previewInFlight = useRef(false)
  const createInFlight = useRef(false)

  const previewMutation = useMutation({
    mutationFn: (body: Record<string, string | null>) =>
      adminJson<Preview>(previewPath, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
  })

  const createMutation = useMutation({
    mutationFn: (confirmationToken: string) =>
      adminJson<Record<string, unknown>>(createPath, {
        method: 'POST',
        body: JSON.stringify({ confirmationToken }),
      }),
  })

  function updateValue(name: string, value: string) {
    previewGeneration.current += 1
    setValues((current) => ({ ...current, [name]: value }))
    setPreview(null)
    setCreated(null)
    setError(null)
  }

  function previewPayload(): Record<string, string | null> {
    return Object.fromEntries(
      inputs.map((input) => {
        const value = values[input.name] ?? ''
        const normalizedValue = value.trim()

        return [
          input.name,
          input.required === false && !normalizedValue ? null : value,
        ]
      }),
    )
  }

  function handlePreview(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (previewInFlight.current) {
      return
    }
    previewInFlight.current = true
    setError(null)
    setFieldErrors({})
    setPreview(null)
    setCreated(null)

    const generation = ++previewGeneration.current

    previewMutation.mutate(previewPayload(), {
      onSuccess: (result) => {
        if (generation !== previewGeneration.current) {
          return
        }
        setPreview(result)
        setCreated(null)
        if (onCompleted) {
          const decision = registrationStepDecision(result)
          if (decision.action === 'skip') {
            onCompleted(decision.existingId, 'duplicate')
          }
        }
      },
      onError: (reason) => {
        if (generation !== previewGeneration.current) {
          return
        }
        setFieldErrors(fieldErrorsFor(reason))
        setError(messageFor(reason))
      },
      onSettled: () => {
        previewInFlight.current = false
      },
    })
  }

  function handleCreate() {
    if (!preview?.confirmationToken || createInFlight.current) {
      return
    }
    createInFlight.current = true
    const generation = previewGeneration.current

    setError(null)
    setFieldErrors({})
    createMutation.mutate(preview.confirmationToken, {
      onSuccess: (result) => {
        if (generation !== previewGeneration.current) {
          return
        }
        setCreated(result)
        void queryClient.invalidateQueries({ queryKey: ['admin'] })
        if (onCompleted && typeof result.id === 'string') {
          onCompleted(result.id, 'created')
        }
      },
      onError: (reason) => {
        if (generation !== previewGeneration.current) {
          return
        }
        setFieldErrors(fieldErrorsFor(reason))
        setError(messageFor(reason))
      },
      onSettled: () => {
        createInFlight.current = false
      },
    })
  }

  const candidate = formatRecord(preview?.candidate ?? null)
  const existing = formatRecord(preview?.existingResource ?? null)
  const createdResource = formatRecord(created)

  return (
    <div className={styles.flow}>
      <form className={styles.form} onSubmit={handlePreview} noValidate>
        {inputs.map((input) => {
          const fieldId = `${fieldIdPrefix}-${input.name}`
          const errorId = `${fieldId}-error`

          return input.options ? (
            <div key={input.name} className={styles.selectField}>
              <label htmlFor={fieldId}>{input.label}</label>
              <select
                id={fieldId}
                name={input.name}
                value={values[input.name] ?? ''}
                onChange={(event) => updateValue(input.name, event.target.value)}
                required={input.required ?? true}
                aria-invalid={fieldErrors[input.name] ? true : undefined}
                aria-describedby={fieldErrors[input.name] ? errorId : undefined}
              >
                <option value="">선택하세요</option>
                {input.options.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
              {fieldErrors[input.name] ? <small id={errorId} className={styles.error}>{fieldErrors[input.name]}</small> : null}
            </div>
          ) : (
            <Field
              key={input.name}
              label={input.label}
              name={input.name}
              type={input.type ?? 'text'}
              value={values[input.name] ?? ''}
              onChange={(event) => updateValue(input.name, event.target.value)}
              error={fieldErrors[input.name]}
              required={input.required ?? true}
            />
          )
        })}
        {error ? <p className={styles.error} role="alert">{error}</p> : null}
        <Button type="submit" disabled={previewMutation.isPending}>
          {previewMutation.isPending ? '미리보기 확인 중…' : '미리보기 확인'}
        </Button>
      </form>

      {preview ? (
        <section className={styles.result} aria-live="polite">
          <h2>미리보기 결과</h2>
          {preview.decision === 'READY' ? (
            <>
              <p>등록할 {resourceName} 정보를 확인했습니다.</p>
              {candidate ? <pre>{candidate}</pre> : null}
              {preview.expiresAt ? <p>확인 유효 시간: {preview.expiresAt}</p> : null}
              <Button onClick={handleCreate} disabled={createMutation.isPending}>
                {createMutation.isPending ? '등록 중…' : '확정 등록'}
              </Button>
            </>
          ) : null}
          {preview.decision === 'DUPLICATE' ? (
            <>
              <p className={styles.error}>이미 등록된 {resourceName}입니다. 중복 등록할 수 없습니다.</p>
              {existing ? <pre>{existing}</pre> : null}
            </>
          ) : null}
          {preview.decision === 'REVIEW_REQUIRED' ? (
            <>
              <p className={styles.notice}>자동 등록할 수 없어 검토가 필요합니다. 내용을 확인한 뒤 다시 시도해 주세요.</p>
              {candidate ? <pre>{candidate}</pre> : null}
            </>
          ) : null}
        </section>
      ) : null}

      {createdResource ? (
        <section className={styles.success} aria-live="polite">
          <h2>등록 완료</h2>
          <p>{resourceName} 등록을 완료했습니다. 방문 관계 등록에 사용할 식별자를 확인해 주세요.</p>
          <pre>{createdResource}</pre>
        </section>
      ) : null}
    </div>
  )
}
