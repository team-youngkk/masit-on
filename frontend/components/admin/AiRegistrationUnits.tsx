'use client'

import Link from 'next/link'
import { useState } from 'react'

import { Button } from '@/components/ui/Button'
import { StatePanel } from '@/components/ui/StatePanel'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { AdminApiError } from '@/lib/admin/api'
import {
  aiExtractionMessageFor,
  AI_BLOCK_REASON_LABELS,
  aiValidationFailureMessageFor,
  aiValidationConflictFrom,
  aiValidationRetryActionFor,
  registerAiRegistrationUnit,
  reviewAiVideoExtraction,
  type AiRegistrationUnit,
  type AiRequiredSupplementField,
  type AiReviewSupplements,
  type AiValidationConflict,
} from '@/lib/admin/ai-video-extractions'
import { exceptionActionsFor, registrationUnitActionsFor, reviewRequest } from '@/lib/admin/ai-video-extractions-coordination'
import { CATEGORY_OPTIONS } from '@/lib/restaurants-api'

import styles from './AiVideoExtractionScreen.module.css'
import buttonStyles from '../ui/Button.module.css'

const SUPPLEMENT_FIELD_LABELS: Record<AiRequiredSupplementField, string> = {
  kakaoPlaceUrl: 'Kakao 장소 URL',
  foodCategoryId: '대표 음식 카테고리',
}

const MANUAL_OVERRIDE_LABELS: Record<'ROLLED_BACK' | 'DISCARDED', string> = {
  ROLLED_BACK: '관리자가 롤백을 완료했습니다.',
  DISCARDED: '관리자가 폐기를 완료했습니다.',
}

type UnitsProps = {
  jobId: string
  units: AiRegistrationUnit[]
  refresh: (message?: string) => Promise<void>
  onRequestRetry: () => void
}

export function AiRegistrationUnits({ jobId, units, refresh, onRequestRetry }: UnitsProps) {
  if (!units.length) {
    return <p className={styles.hint}>등록 단위가 없습니다.</p>
  }

  return (
    <ul className={styles.candidates}>
      {units.map((unit) => (
        <RegistrationUnitCard key={unit.unitId} jobId={jobId} unit={unit} refresh={refresh} onRequestRetry={onRequestRetry} />
      ))}
    </ul>
  )
}

type CardProps = {
  jobId: string
  unit: AiRegistrationUnit
  refresh: (message?: string) => Promise<void>
  onRequestRetry: () => void
}

function RegistrationUnitCard({ jobId, unit, refresh, onRequestRetry }: CardProps) {
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState(false)
  const [conflict, setConflict] = useState<AiValidationConflict | null>(null)
  const [reason, setReason] = useState('')
  const [supplementValue, setSupplementValue] = useState('')
  const [supplementField, setSupplementField] = useState<AiRequiredSupplementField | null>(null)
  const [categoryFormOpen, setCategoryFormOpen] = useState(false)
  const [categoryReason, setCategoryReason] = useState('')
  const [foodCategoryId, setFoodCategoryId] = useState('')
  const [rollbackFormOpen, setRollbackFormOpen] = useState(false)
  const [rollbackReason, setRollbackReason] = useState('')

  const actions = registrationUnitActionsFor(unit)

  async function handleConflict(caught: unknown): Promise<boolean> {
    if (caught instanceof AdminApiError && caught.status === 409) {
      await refresh('다른 변경과 충돌했습니다. 최신 상태를 다시 조회했습니다. 검토 후 다시 진행해 주세요.')
      return true
    }
    return false
  }

  async function register() {
    setBusy(true); setError(false); setNotice(''); setConflict(null)
    try {
      await registerAiRegistrationUnit(jobId, unit.unitId)
      await refresh(`${unit.restaurantName} 등록을 완료했습니다.`)
    } catch (caught) {
      if (await handleConflict(caught)) return
      const validationConflict = aiValidationConflictFrom(caught)
      if (validationConflict) {
        setConflict(validationConflict)
        setError(true)
        setNotice('등록 조건을 충족하지 못했습니다. 아래 안내에 따라 처리해 주세요.')
      } else {
        setError(true)
        setNotice(aiExtractionMessageFor(caught))
      }
    } finally {
      setBusy(false)
    }
  }

  async function submitSupplement(field: AiRequiredSupplementField) {
    if (!reason.trim() || !supplementValue.trim()) return
    setSupplementField(field)
    setBusy(true); setError(false)
    try {
      const supplements: AiReviewSupplements = field === 'kakaoPlaceUrl'
        ? { kakaoPlaceUrl: supplementValue.trim() }
        : { foodCategoryId: supplementValue.trim() }
      const request = reviewRequest('CONFIRM', unit.unitId, unit.reviewStatus, supplements)
      await reviewAiVideoExtraction(jobId, request.decision, request.unitId, request.expectedReviewStatus, reason, { supplements: request.supplements })
      setConflict(null); setReason(''); setSupplementValue('')
      setSupplementField(null)
      await refresh(`${unit.restaurantName} 보충 입력으로 등록을 완료했습니다.`)
    } catch (caught) {
      if (await handleConflict(caught)) return
      const validationConflict = aiValidationConflictFrom(caught)
      if (validationConflict) {
        setConflict(validationConflict)
        setError(true)
        setNotice(validationConflict.validationFailureReason ? '' : aiExtractionMessageFor(caught))
      } else {
        setError(true); setNotice(aiExtractionMessageFor(caught))
      }
    } finally {
      setBusy(false)
    }
  }

  function retryRegistration() {
    if (conflict && aiValidationRetryActionFor(conflict.validationFailureReason, supplementField) === 'SUPPLEMENT' && supplementField) {
      void submitSupplement(supplementField)
      return
    }
    void register()
  }

  async function discard() {
    if (!reason.trim()) return
    setBusy(true); setError(false)
    try {
      const request = reviewRequest('DISCARD', unit.unitId, unit.reviewStatus)
      await reviewAiVideoExtraction(jobId, request.decision, request.unitId, request.expectedReviewStatus, reason)
      setReason('')
      await refresh(`${unit.restaurantName}을(를) 폐기했습니다.`)
    } catch (caught) {
      if (await handleConflict(caught)) return
      setError(true); setNotice(aiExtractionMessageFor(caught))
    } finally {
      setBusy(false)
    }
  }

  async function submitRollback() {
    if (!rollbackReason.trim()) return
    setBusy(true); setError(false)
    try {
      const request = reviewRequest('ROLLBACK', unit.unitId, unit.reviewStatus)
      await reviewAiVideoExtraction(jobId, request.decision, request.unitId, request.expectedReviewStatus, rollbackReason)
      setRollbackFormOpen(false); setRollbackReason('')
      await refresh(`${unit.restaurantName} 등록을 롤백했습니다.`)
    } catch (caught) {
      if (await handleConflict(caught)) return
      setError(true); setNotice(aiExtractionMessageFor(caught))
    } finally {
      setBusy(false)
    }
  }

  async function submitCategoryAdjustment() {
    if (!categoryReason.trim() || !foodCategoryId) return
    setBusy(true); setError(false)
    try {
      const request = reviewRequest('ADJUST_CATEGORY', unit.unitId, unit.reviewStatus, { foodCategoryId })
      await reviewAiVideoExtraction(jobId, request.decision, request.unitId, request.expectedReviewStatus, categoryReason, { supplements: request.supplements })
      setCategoryFormOpen(false); setCategoryReason(''); setFoodCategoryId('')
      await refresh(`${unit.restaurantName} 카테고리를 보정했습니다.`)
    } catch (caught) {
      if (await handleConflict(caught)) return
      setError(true); setNotice(aiExtractionMessageFor(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className={styles.candidate}>
      <div className={styles.toolbar}>
        <h3>{unit.restaurantName}</h3>
        <StatusBadge tone={unitTone(unit)}>{unit.reviewStatus}{unit.manualOverrideType ? ` · ${unit.manualOverrideType}` : ''}</StatusBadge>
      </div>

      {unit.manualOverrideType ? <p className={styles.hint}>{MANUAL_OVERRIDE_LABELS[unit.manualOverrideType]}</p> : null}
      {!unit.manualOverrideType && unit.blockReason ? <p className={styles.hint}>{AI_BLOCK_REASON_LABELS[unit.blockReason] ?? unit.blockReason}</p> : null}

      {unit.placeDecision && unit.categoryDecision ? (
        <dl className={styles.unitDetails}>
          <div>
            <dt>Kakao 장소</dt>
            <dd><a href={unit.placeDecision.kakaoPlaceUrl} target="_blank" rel="noreferrer">{unit.placeDecision.kakaoPlaceUrl}</a> · {unit.placeDecision.roadAddress} · {unit.placeDecision.matchedBy}</dd>
          </div>
          <div>
            <dt>대표 카테고리</dt>
            <dd>{unit.categoryDecision.foodCategoryName} · {unit.categoryDecision.resolvedBy}</dd>
          </div>
          <div>
            <dt>등록 결과</dt>
            <dd>맛집 {unit.registeredRestaurantId} · 유튜버 {unit.registeredCreatorId} · 영상 {unit.registeredVideoId} · 방문 {unit.registeredVisitId}</dd>
          </div>
          <div>
            <dt>자원 재사용</dt>
            <dd>{unit.reusedResources.length ? `${unit.reusedResources.join(', ')} 재사용` : '신규 생성'}</dd>
          </div>
        </dl>
      ) : null}

      {actions.registerable ? (
        <div className={styles.actions}>
          <Button disabled={busy} onClick={() => void register()}>{busy ? '등록 실행 중…' : '등록 실행'}</Button>
        </div>
      ) : null}

      {conflict ? (
        <ExceptionPanel
          unit={unit}
          conflict={conflict}
          busy={busy}
          reason={reason}
          onReasonChange={setReason}
          supplementValue={supplementValue}
          onSupplementValueChange={setSupplementValue}
          onSubmitSupplement={submitSupplement}
          onRetry={retryRegistration}
          onRequestRetryExtraction={onRequestRetry}
          onDiscard={discard}
        />
      ) : null}

      {actions.adjustCategory || actions.rollback ? (
        <div className={styles.actions}>
          {actions.adjustCategory ? <Button variant="secondary" disabled={busy} onClick={() => setCategoryFormOpen((open) => !open)}>카테고리 보정</Button> : null}
          {actions.rollback ? <Button variant="secondary" disabled={busy} onClick={() => setRollbackFormOpen((open) => !open)}>롤백</Button> : null}
        </div>
      ) : null}

      {categoryFormOpen ? (
        <form className={styles.retryForm} onSubmit={(event) => { event.preventDefault(); void submitCategoryAdjustment() }}>
          <label>
            대표 음식 카테고리
            <select required value={foodCategoryId} onChange={(event) => setFoodCategoryId(event.target.value)}>
              <option value="">선택하세요</option>
              {CATEGORY_OPTIONS.map((option) => <option key={option} value={option}>{option}</option>)}
            </select>
          </label>
          <label>보정 사유<input required value={categoryReason} onChange={(event) => setCategoryReason(event.target.value)} /></label>
          <div className={styles.actions}>
            <Button type="submit" disabled={busy}>카테고리 보정 제출</Button>
            <Button variant="secondary" disabled={busy} onClick={() => setCategoryFormOpen(false)}>취소</Button>
          </div>
        </form>
      ) : null}

      {rollbackFormOpen ? (
        <form className={styles.retryForm} onSubmit={(event) => { event.preventDefault(); void submitRollback() }}>
          <label>롤백 사유<input required value={rollbackReason} onChange={(event) => setRollbackReason(event.target.value)} /></label>
          <div className={styles.actions}>
            <Button type="submit" variant="secondary" disabled={busy}>롤백 제출</Button>
            <Button variant="secondary" disabled={busy} onClick={() => setRollbackFormOpen(false)}>취소</Button>
          </div>
        </form>
      ) : null}

      {notice ? <p className={error ? styles.error : styles.notice} role={error ? 'alert' : 'status'}>{notice}</p> : null}
    </li>
  )
}

function ExceptionPanel({
  unit,
  conflict,
  busy,
  reason,
  onReasonChange,
  supplementValue,
  onSupplementValueChange,
  onSubmitSupplement,
  onRetry,
  onRequestRetryExtraction,
  onDiscard,
}: {
  unit: AiRegistrationUnit
  conflict: AiValidationConflict
  busy: boolean
  reason: string
  onReasonChange: (value: string) => void
  supplementValue: string
  onSupplementValueChange: (value: string) => void
  onSubmitSupplement: (field: AiRequiredSupplementField) => void
  onRetry: () => void
  onRequestRetryExtraction: () => void
  onDiscard: () => void
}) {
  const actions = exceptionActionsFor(unit.reviewStatus, conflict.recoveryPaths)
  const supplementField = conflict.requiredSupplements[0]
  const validationFailureMessage = aiValidationFailureMessageFor(conflict.validationFailureReason)

  if (!actions.length) {
    return (
      <StatePanel
        tone="danger"
        headingLevel={4}
        title={conflict.blockReason ? AI_BLOCK_REASON_LABELS[conflict.blockReason] ?? conflict.blockReason : '등록 조건을 충족하지 못했습니다.'}
        description={
          <>
            {validationFailureMessage ? <p className={styles.error}>{validationFailureMessage}</p> : null}
            <p>이 예외는 자동 복구할 수 없습니다. 새 작업으로 다시 추출해 주세요.</p>
          </>
        }
        traceId={conflict.traceId ?? null}
      />
    )
  }

  return (
    <StatePanel
      tone="warning"
      headingLevel={4}
      title={conflict.blockReason ? AI_BLOCK_REASON_LABELS[conflict.blockReason] ?? conflict.blockReason : '등록 조건을 충족하지 못했습니다.'}
      traceId={conflict.traceId ?? null}
      description={
        <>
          {validationFailureMessage ? <p className={styles.error}>{validationFailureMessage}</p> : null}
          <div className={styles.actions}>
            {actions.includes('SUPPLEMENT') && supplementField ? (
              <form
                className={styles.retryForm}
                onSubmit={(event) => { event.preventDefault(); onSubmitSupplement(supplementField) }}
              >
                <label>
                  {SUPPLEMENT_FIELD_LABELS[supplementField]}
                  <input required value={supplementValue} onChange={(event) => onSupplementValueChange(event.target.value)} />
                </label>
                <label>보충 사유<input required value={reason} onChange={(event) => onReasonChange(event.target.value)} /></label>
                <Button type="submit" disabled={busy}>보충 입력 제출</Button>
              </form>
            ) : null}
            {actions.includes('REEXTRACT') ? <Button variant="secondary" disabled={busy} onClick={onRequestRetryExtraction}>보완 텍스트 재추출</Button> : null}
            {actions.includes('MANUAL_REGISTRATION') ? <Link href="/admin/restaurants/new" className={`${buttonStyles.button} ${buttonStyles.secondary}`}>기존 수동 등록으로 전환</Link> : null}
            {actions.includes('EXISTING_RESOURCE') ? <p className={styles.hint}>이미 등록된 맛집·방문 관계를 관리자 화면에서 직접 확인해 주세요.</p> : null}
            {actions.includes('RETRY') ? <Button variant="secondary" disabled={busy} onClick={onRetry}>등록 재실행</Button> : null}
            {actions.includes('DISCARD') ? (
              <form className={styles.retryForm} onSubmit={(event) => { event.preventDefault(); onDiscard() }}>
                <label>폐기 사유<input required value={reason} onChange={(event) => onReasonChange(event.target.value)} /></label>
                <Button type="submit" variant="secondary" disabled={busy}>폐기</Button>
              </form>
            ) : null}
          </div>
        </>
      }
    />
  )
}

function unitTone(unit: AiRegistrationUnit): 'success' | 'warning' | 'danger' | 'neutral' {
  if (unit.manualOverrideType === 'ROLLED_BACK' || unit.manualOverrideType === 'DISCARDED') return 'neutral'
  if (unit.reviewStatus === 'AUTO_CONFIRMED' || unit.reviewStatus === 'MANUAL_OVERRIDE') return 'success'
  if (unit.reviewStatus === 'AUTO_BLOCKED') return 'warning'
  if (unit.reviewStatus === 'AUTO_REJECTED') return 'danger'
  return 'neutral'
}
