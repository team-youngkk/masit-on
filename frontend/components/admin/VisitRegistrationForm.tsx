'use client'

import { useId, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'

import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { adminJson, fieldErrorsFor, messageFor } from '@/lib/admin/api'
import { fetchCreatorReferences } from '@/lib/creators-api'

import styles from './admin.module.css'

type Reference = { id: string; name: string }
type RestaurantResponse = { items: Array<{ id: string; name: string }> }
type VisitResponse = { id: string; restaurantId: string; creatorId: string; videoId: string }

const visitStages = [
  { key: 'input', label: '입력', description: '관계 정보 작성' },
  { key: 'evidence', label: '근거 확인', description: '방문 근거 확인' },
  { key: 'complete', label: '완료', description: '관계 식별자 기록' },
] as const

async function fetchRestaurantReferences(query: string): Promise<Reference[]> {
  const params = new URLSearchParams({ page: '1', size: '50' })
  if (query.trim()) {
    params.set('query', query.trim())
  }

  const response = await fetch(`/api/restaurants?${params.toString()}`)
  if (!response.ok) {
    throw new Error('맛집 참조 목록을 불러오지 못했습니다.')
  }

  const restaurants = (await response.json()) as RestaurantResponse
  return restaurants.items.map(({ id, name }) => ({ id, name }))
}

export function VisitRegistrationForm() {
  const fieldIdPrefix = useId()
  const submitInFlight = useRef(false)
  const [restaurantQuery, setRestaurantQuery] = useState('')
  const [restaurantId, setRestaurantId] = useState('')
  const [creatorId, setCreatorId] = useState('')
  const [videoId, setVideoId] = useState('')
  const [evidenceConfirmed, setEvidenceConfirmed] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<VisitResponse | null>(null)
  const restaurants = useQuery({
    queryKey: ['admin', 'restaurant-reference', restaurantQuery],
    queryFn: () => fetchRestaurantReferences(restaurantQuery),
  })
  const creators = useQuery({ queryKey: ['admin', 'creator-reference'], queryFn: fetchCreatorReferences })

  const mutation = useMutation({
    mutationFn: () =>
      adminJson<VisitResponse>('/api/admin/visit-relationships', {
        method: 'POST',
        body: JSON.stringify({ restaurantId, creatorId, videoId, visitEvidenceConfirmed: evidenceConfirmed }),
      }),
  })

  const isReferenceLoading = restaurants.isLoading || creators.isLoading
  const currentStage = result ? 'complete' : mutation.isPending ? 'evidence' : 'input'
  const currentStageIndex = visitStages.findIndex((stage) => stage.key === currentStage)

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitInFlight.current) {
      return
    }
    submitInFlight.current = true
    setError(null)
    setFieldErrors({})
    setResult(null)
    mutation.mutate(undefined, {
      onSuccess: setResult,
      onError: (reason) => {
        setFieldErrors(fieldErrorsFor(reason))
        setError(messageFor(reason))
      },
      onSettled: () => {
        submitInFlight.current = false
      },
    })
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate aria-busy={isReferenceLoading || mutation.isPending}>
      <div className={styles.formHeader}>
        <div>
          <p className={styles.eyebrow}>방문 근거 등록</p>
          <h2>방문 관계 등록 흐름</h2>
          <p>맛집·유튜버·영상의 관계와 방문 근거를 확인한 뒤 등록합니다.</p>
        </div>
        <span className={styles.formState}>{mutation.isPending ? '등록 확인 중' : result ? '등록 완료' : '입력 대기'}</span>
      </div>
      <ol className={`${styles.stepRail} ${styles.visitStepRail}`} aria-label="방문 관계 등록 진행 단계">
        {visitStages.map((stage, index) => {
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
                <small className={styles.stepState}>{stage.description}</small>
              </span>
            </li>
          )
        })}
      </ol>
      <Field
        label="맛집 이름 검색"
        name="restaurantQuery"
        value={restaurantQuery}
        onChange={(event) => {
          setRestaurantQuery(event.target.value)
          setRestaurantId('')
        }}
      />
      <p className={styles.help}>등록할 맛집 이름을 입력하면 최대 50개의 일치 항목을 선택할 수 있습니다.</p>
      {isReferenceLoading ? <p className={styles.loading} role="status" aria-live="polite">참조 목록을 불러오는 중입니다.</p> : null}
      {restaurants.isError || creators.isError ? <p className={styles.error} role="alert">참조 목록을 불러오지 못했습니다. 새로고침해 주세요.</p> : null}
      <label className={styles.selectField} htmlFor={`${fieldIdPrefix}-restaurant`}>
        <span>맛집</span>
        <select
          id={`${fieldIdPrefix}-restaurant`}
          value={restaurantId}
          onChange={(event) => setRestaurantId(event.target.value)}
          required
          aria-invalid={fieldErrors.restaurantId ? true : undefined}
          aria-describedby={fieldErrors.restaurantId ? `${fieldIdPrefix}-restaurant-error` : undefined}
        >
          <option value="">맛집을 선택하세요</option>
          {restaurants.data?.map((restaurant) => <option key={restaurant.id} value={restaurant.id}>{restaurant.name}</option>)}
        </select>
        {fieldErrors.restaurantId ? <small id={`${fieldIdPrefix}-restaurant-error`} className={styles.error}>{fieldErrors.restaurantId}</small> : null}
      </label>
      <label className={styles.selectField} htmlFor={`${fieldIdPrefix}-creator`}>
        <span>유튜버</span>
        <select
          id={`${fieldIdPrefix}-creator`}
          value={creatorId}
          onChange={(event) => setCreatorId(event.target.value)}
          required
          aria-invalid={fieldErrors.creatorId ? true : undefined}
          aria-describedby={fieldErrors.creatorId ? `${fieldIdPrefix}-creator-error` : undefined}
        >
          <option value="">유튜버를 선택하세요</option>
          {creators.data?.map((creator) => <option key={creator.id} value={creator.id}>{creator.channelName}</option>)}
        </select>
        {fieldErrors.creatorId ? <small id={`${fieldIdPrefix}-creator-error`} className={styles.error}>{fieldErrors.creatorId}</small> : null}
      </label>
      <Field
        label="영상 ID"
        name="videoId"
        value={videoId}
        onChange={(event) => setVideoId(event.target.value)}
        error={fieldErrors.videoId}
        required
      />
      <p className={styles.help}>영상 선택 API는 아직 제공되지 않아, 영상 등록 완료 결과의 ID를 입력합니다.</p>
      <label className={styles.checkbox}>
        <input
          type="checkbox"
          checked={evidenceConfirmed}
          onChange={(event) => setEvidenceConfirmed(event.target.checked)}
          aria-invalid={fieldErrors.visitEvidenceConfirmed ? true : undefined}
          aria-describedby={fieldErrors.visitEvidenceConfirmed ? `${fieldIdPrefix}-evidence-error` : undefined}
        />
        방문 근거를 확인했습니다.
      </label>
      {fieldErrors.visitEvidenceConfirmed ? <p id={`${fieldIdPrefix}-evidence-error`} className={styles.error}>{fieldErrors.visitEvidenceConfirmed}</p> : null}
      {error ? <p className={styles.error} role="alert">{error}</p> : null}
      <div className={styles.formActions}>
        <Button type="submit" disabled={mutation.isPending || isReferenceLoading}>
          {mutation.isPending ? '등록 중…' : '방문 관계 등록'}
        </Button>
      </div>
      {result ? <section className={styles.success} aria-live="polite"><h2>등록 완료</h2><p>방문 관계 ID: {result.id}</p></section> : null}
    </form>
  )
}
