'use client'

import { useId, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'

import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { adminJson, fieldErrorsFor, messageFor } from '@/lib/admin/api'
import { registrationStepDecision } from '@/lib/admin/registration-progression'

import { RegistrationDuplicateResult } from './RegistrationDuplicateResult'
import styles from './admin.module.css'
import buttonStyles from '../ui/Button.module.css'

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

type RegistrationStageKey = 'input' | 'validation' | 'review' | 'confirm' | 'complete'

const registrationStages: Array<{ key: RegistrationStageKey; label: string; description: string }> = [
  { key: 'input', label: '입력', description: '기본 정보 작성' },
  { key: 'validation', label: '검증', description: '외부 근거 확인' },
  { key: 'review', label: '중복·검토', description: '판정과 예외 확인' },
  { key: 'confirm', label: '확정', description: '등록 전 최종 확인' },
  { key: 'complete', label: '완료', description: '결과 식별자 기록' },
]

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
  const duplicateCompletionInFlight = useRef(false)

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
    duplicateCompletionInFlight.current = false
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
    duplicateCompletionInFlight.current = false

    const generation = ++previewGeneration.current

    previewMutation.mutate(previewPayload(), {
      onSuccess: (result) => {
        if (generation !== previewGeneration.current) {
          return
        }
        setPreview(result)
        setCreated(null)
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

  function handleDuplicateContinue() {
    if (!preview || !onCompleted || duplicateCompletionInFlight.current) {
      return
    }
    const decision = registrationStepDecision(preview)
    if (decision.action !== 'skip') {
      return
    }
    duplicateCompletionInFlight.current = true
    onCompleted(decision.existingId, 'duplicate')
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
  const currentStage: RegistrationStageKey = created
    ? 'complete'
    : preview?.decision === 'READY'
      ? 'confirm'
      : preview
        ? 'review'
        : previewMutation.isPending
          ? 'validation'
          : 'input'
  const currentStageIndex = registrationStages.findIndex((stage) => stage.key === currentStage)
  const currentStageDescription = registrationStages[currentStageIndex]?.description ?? ''

  return (
    <div className={styles.flow}>
      <section className={styles.flowOverview} aria-labelledby={`${fieldIdPrefix}-flow-heading`}>
        <div className={styles.flowHeading}>
          <p className={styles.eyebrow}>등록 작업</p>
          <h2 id={`${fieldIdPrefix}-flow-heading`}>{resourceName} 등록 흐름</h2>
          <p>입력한 정보를 검증하고 판정을 확인한 뒤 등록 결과를 남깁니다.</p>
        </div>
        <ol className={styles.stepRail} aria-label={`${resourceName} 등록 진행 단계`}>
          {registrationStages.map((stage, index) => {
            const state = index < currentStageIndex ? 'complete' : index === currentStageIndex ? 'current' : 'upcoming'
            const stateClass = state === 'complete'
              ? styles.stepComplete
              : state === 'current'
                ? styles.stepCurrent
                : styles.stepUpcoming

            return (
              <li key={stage.key} className={`${styles.step} ${stateClass}`} aria-current={state === 'current' ? 'step' : undefined}>
                <span className={styles.stepMarker} aria-hidden="true" />
                <span className={styles.stepCopy}>
                  <strong className={styles.stepLabel}>{stage.label}</strong>
                  <small className={styles.stepState}>{state === 'current' ? currentStageDescription : stage.description}</small>
                </span>
              </li>
            )
          })}
        </ol>
        <p className={styles.flowStatus}>현재 단계: <strong>{registrationStages[currentStageIndex]?.label}</strong></p>
      </section>

      <form className={styles.form} onSubmit={handlePreview} noValidate aria-busy={previewMutation.isPending}>
        <div className={styles.formHeader}>
          <div>
            <h3>기본 정보 입력</h3>
            <p>확인할 값을 입력한 뒤 미리보기 확인을 눌러 다음 단계로 이동합니다.</p>
          </div>
          <span className={styles.formState}>{previewMutation.isPending ? '검증 중' : '입력 대기'}</span>
        </div>
        <div className={styles.inputGrid}>
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
        </div>
        {error ? <p className={styles.error} role="alert">{error}</p> : null}
        <div className={styles.formActions}>
          <Button type="submit" disabled={previewMutation.isPending}>
            {previewMutation.isPending ? '미리보기 확인 중…' : '미리보기 확인'}
          </Button>
        </div>
      </form>

      {preview ? (
        <section className={`${styles.result} ${preview.decision === 'READY' ? styles.resultReady : styles.resultReview}`} aria-live="polite">
          <div className={styles.resultHeader}>
            <div>
              <p className={styles.eyebrow}>{preview.decision === 'READY' ? '확정 단계' : '검토 단계'}</p>
              <h2>미리보기 결과</h2>
            </div>
            <span className={styles.formState}>{preview.decision === 'READY' ? '확정 대기' : '확인 필요'}</span>
          </div>
          {preview.decision === 'READY' ? (
            <>
              <p>등록할 {resourceName} 정보를 확인했습니다.</p>
              {previewPath === '/api/admin/restaurant-registration-previews' ? <p>지역은 검증된 도로명주소를 기준으로 자동 연결됩니다.</p> : null}
              {candidate ? <pre>{candidate}</pre> : null}
              {preview.expiresAt ? <p>확인 유효 시간: {preview.expiresAt}</p> : null}
              <Button onClick={handleCreate} disabled={createMutation.isPending}>
                {createMutation.isPending ? '등록 중…' : '확정 등록'}
              </Button>
            </>
          ) : null}
          {preview.decision === 'DUPLICATE' ? (
            <div className={styles.reviewPanel}>
              <p className={styles.panelLabel}>중복 확인</p>
              <RegistrationDuplicateResult
                resourceName={resourceName}
                existing={existing}
                onContinue={onCompleted ? handleDuplicateContinue : undefined}
                errorClassName={styles.error}
                buttonClassName={`${buttonStyles.button} ${buttonStyles.secondary}`}
              />
            </div>
          ) : null}
          {preview.decision === 'REVIEW_REQUIRED' ? (
            <div className={styles.reviewPanel}>
              <p className={styles.panelLabel}>수동 검토</p>
              <p className={styles.notice}>자동 등록할 수 없어 검토가 필요합니다. 내용을 확인한 뒤 다시 시도해 주세요.</p>
              {candidate ? <pre>{candidate}</pre> : null}
            </div>
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
