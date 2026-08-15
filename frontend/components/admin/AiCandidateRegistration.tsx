'use client'

import { useEffect, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'

import { Button } from '@/components/ui/Button'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { adminJson, fieldErrorsFor, messageFor } from '@/lib/admin/api'
import {
  addressCandidates,
  buildVisitRelationshipPayload,
  channelUrlFromChannelId,
  placeSearchSeed,
  placeSearchSeedIdentity,
  prefillFromPlace,
  isCurrentAsyncRequest,
  isCurrentSearchRequest,
  visitEvidenceCandidates,
} from '@/lib/admin/ai-candidate-registration-coordination'
import type { AiCandidate, AiExtractionDetail } from '@/lib/admin/ai-video-extractions'
import { searchAdminPlaceCandidates, type AdminPlaceSearchResult } from '@/lib/admin/restaurant-place-search'
import { registrationCompletionTransition } from '@/lib/admin/registration-progression'

import { RegistrationFlow } from './RegistrationFlow'
import styles from './AiVideoExtractionScreen.module.css'
import flowStyles from './admin.module.css'

const CATEGORY_OPTIONS = ['한식', '중식', '일식', '양식', '동남아 음식', '인도·남아시아 음식', '분식', '카페·디저트', '술집·주점', '기타']

type Step = 'search' | 'restaurant' | 'creator' | 'video' | 'visit' | 'done'

type PlacePrefill = ReturnType<typeof prefillFromPlace>

type VisitResult = { id: string }

type PlaceSearchSeed = ReturnType<typeof placeSearchSeed>

type PlaceSearchRequest = {
  identity: string
  seed: PlaceSearchSeed
}

type PlaceSearchView =
  | { status: 'idle' }
  | { status: 'pending'; identity: string }
  | { status: 'success'; identity: string; data: AdminPlaceSearchResult[] }
  | { status: 'error'; identity: string; error: unknown }

type VisitRequest = {
  sessionIdentity: string
  generation: number
  payload: ReturnType<typeof buildVisitRelationshipPayload>
}

export function AiCandidateRegistration({
  detail,
  candidate,
  onCancel,
}: {
  detail: AiExtractionDetail
  candidate: AiCandidate
  onCancel: () => void
}) {
  const addresses = addressCandidates(detail.candidates)
  const [addressHint, setAddressHint] = useState<string | null>(addresses.length === 1 ? addresses[0].value ?? null : null)
  const [step, setStep] = useState<Step>('search')
  const [name, setName] = useState(candidate.value?.trim() ?? '')
  const [searchView, setSearchView] = useState<PlaceSearchView>({ status: 'idle' })
  const [pendingSearchIdentities, setPendingSearchIdentities] = useState<Set<string>>(() => new Set())
  const [placePrefill, setPlacePrefill] = useState<PlacePrefill | null>(null)
  const [manualPlaceEntry, setManualPlaceEntry] = useState(false)
  const [restaurantId, setRestaurantId] = useState<string | null>(null)
  const [creatorId, setCreatorId] = useState<string | null>(null)
  const [videoId, setVideoId] = useState<string | null>(null)
  const [evidenceConfirmed, setEvidenceConfirmed] = useState(false)
  const [visitError, setVisitError] = useState<string | null>(null)
  const [visitFieldErrors, setVisitFieldErrors] = useState<Record<string, string>>({})
  const [visitResult, setVisitResult] = useState<VisitResult | null>(null)
  const [visitPending, setVisitPending] = useState(false)
  const sessionIdentity = JSON.stringify([detail.jobId, candidate.candidateTagId ?? null, candidate.field, candidate.value ?? null])
  const sessionIdentityRef = useRef(sessionIdentity)
  const currentSearchIdentityRef = useRef('')
  const searchInFlightRef = useRef(new Set<string>())
  const visitGenerationRef = useRef(0)
  const visitInFlightRef = useRef(false)

  sessionIdentityRef.current = sessionIdentity
  if (step === 'search') {
    currentSearchIdentityRef.current = searchIdentity(sessionIdentity, placeSearchSeed(name, addressHint))
  }

  const searchMutation = useMutation({
    mutationFn: (request: PlaceSearchRequest) =>
      searchAdminPlaceCandidates(request.seed.name, request.seed.roadAddressHint),
    onSuccess: (data, request) => {
      if (!isCurrentSearchRequest(request.identity, currentSearchIdentityRef.current)) {
        return
      }
      setSearchView({ status: 'success', identity: request.identity, data })
    },
    onError: (error, request) => {
      if (!isCurrentSearchRequest(request.identity, currentSearchIdentityRef.current)) {
        return
      }
      setSearchView({ status: 'error', identity: request.identity, error })
    },
    onSettled: (_data, _error, request) => {
      searchInFlightRef.current.delete(request.identity)
      setPendingSearchIdentities((current) => withoutIdentity(current, request.identity))
    },
  })

  const visitMutation = useMutation({
    mutationFn: (request: VisitRequest) =>
      adminJson<VisitResult>('/api/admin/visit-relationships', { method: 'POST', body: JSON.stringify(request.payload) }),
    onSuccess: (result, request) => {
      if (!isCurrentAsyncRequest({ generation: request.generation, identity: request.sessionIdentity }, visitGenerationRef.current, sessionIdentityRef.current)) {
        return
      }
      setVisitResult(result)
      setStep('done')
    },
    onError: (reason, request) => {
      if (!isCurrentAsyncRequest({ generation: request.generation, identity: request.sessionIdentity }, visitGenerationRef.current, sessionIdentityRef.current)) {
        return
      }
      setVisitFieldErrors(fieldErrorsFor(reason))
      setVisitError(messageFor(reason))
    },
    onSettled: (_data, _error, request) => {
      if (!isCurrentAsyncRequest({ generation: request.generation, identity: request.sessionIdentity }, visitGenerationRef.current, sessionIdentityRef.current)) {
        return
      }
      visitInFlightRef.current = false
      setVisitPending(false)
    },
  })

  useEffect(() => {
    const nextAddressHint = addresses.length === 1 ? addresses[0].value?.trim() || null : null
    const nextName = candidate.value?.trim() ?? ''
    visitGenerationRef.current += 1
    visitInFlightRef.current = false
    currentSearchIdentityRef.current = searchIdentity(sessionIdentity, placeSearchSeed(nextName, nextAddressHint))
    setAddressHint(nextAddressHint)
    setStep('search')
    setName(nextName)
    setSearchView({ status: 'idle' })
    setPlacePrefill(null)
    setManualPlaceEntry(false)
    setRestaurantId(null)
    setCreatorId(null)
    setVideoId(null)
    setEvidenceConfirmed(false)
    setVisitError(null)
    setVisitFieldErrors({})
    setVisitResult(null)
    setVisitPending(false)

    return () => {
      currentSearchIdentityRef.current = ''
      visitGenerationRef.current += 1
      visitInFlightRef.current = false
    }
  // addresses are a snapshot of the session detail; only a selected candidate/session change resets an active flow.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionIdentity])

  function updateSearchInput(nextName: string, nextAddressHint: string | null) {
    currentSearchIdentityRef.current = searchIdentity(sessionIdentity, placeSearchSeed(nextName, nextAddressHint))
    setSearchView({ status: 'idle' })
  }

  function submitPlaceSearch() {
    const seed = placeSearchSeed(name, addressHint)
    const identity = searchIdentity(sessionIdentity, seed)
    if (!seed.name || searchInFlightRef.current.has(identity)) {
      return
    }

    searchInFlightRef.current.add(identity)
    setPendingSearchIdentities((current) => new Set(current).add(identity))
    const request = { identity, seed }
    currentSearchIdentityRef.current = identity
    setSearchView({ status: 'pending', identity })
    searchMutation.mutate(request)
  }

  function selectPlace(place: AdminPlaceSearchResult) {
    currentSearchIdentityRef.current = ''
    setSearchView({ status: 'idle' })
    setPlacePrefill(prefillFromPlace(place))
    setName(place.placeName.trim())
    setManualPlaceEntry(false)
    setStep('restaurant')
  }

  function useManualPlaceEntry() {
    currentSearchIdentityRef.current = ''
    setSearchView({ status: 'idle' })
    setName(name.trim())
    setPlacePrefill(null)
    setManualPlaceEntry(true)
    setStep('restaurant')
  }

  function submitVisit() {
    if (!restaurantId || !creatorId || !videoId || visitInFlightRef.current) {
      return
    }

    visitInFlightRef.current = true
    setVisitPending(true)
    setVisitError(null)
    setVisitFieldErrors({})
    visitMutation.mutate({
      sessionIdentity,
      generation: ++visitGenerationRef.current,
      payload: buildVisitRelationshipPayload(restaurantId, creatorId, videoId, evidenceConfirmed),
    })
  }

  const currentSearchIdentity = searchIdentity(sessionIdentity, placeSearchSeed(name, addressHint))
  const searchPending = pendingSearchIdentities.has(currentSearchIdentity)
  const visibleSearchView = searchView.status !== 'idle' && searchView.identity === currentSearchIdentity ? searchView : { status: 'idle' as const }

  const progress = [
    { label: '맛집 등록', done: restaurantId !== null },
    { label: '유튜버 등록', done: creatorId !== null },
    { label: '영상 등록', done: videoId !== null },
    { label: '방문 관계 등록', done: step === 'done' },
  ]

  return (
    <section className={styles.panel} aria-labelledby="ai-candidate-registration-heading">
      <div className={styles.toolbar}>
        <h2 id="ai-candidate-registration-heading">후보로 등록 진행</h2>
        <Button variant="secondary" onClick={onCancel}>다른 후보 선택</Button>
      </div>
      <p className={styles.meta}>선택한 후보: {candidate.value ?? '값 없음'}</p>
      <ul className={styles.actions}>
        {progress.map((item) => (
          <li key={item.label}>
            <StatusBadge tone={item.done ? 'success' : 'neutral'}>{item.done ? '완료' : '대기'} · {item.label}</StatusBadge>
          </li>
        ))}
      </ul>

      {step === 'search' ? (
        <div className={flowStyles.flow}>
          {addresses.length ? (
            <fieldset className={flowStyles.selectField}>
              <legend>주소 힌트 선택</legend>
              {addresses.map((address, index) => (
                <label key={`${address.value}-${index}`} className={flowStyles.checkbox}>
                  <input
                    type="radio"
                    name="address-hint"
                    checked={addressHint === (address.value?.trim() || null)}
                    onChange={() => {
                      const nextAddressHint = address.value?.trim() || null
                      updateSearchInput(name, nextAddressHint)
                      setAddressHint(nextAddressHint)
                    }}
                  />
                  {address.value ?? '값 없음'}
                </label>
              ))}
              <label className={flowStyles.checkbox}>
                <input type="radio" name="address-hint" checked={addressHint === null} onChange={() => {
                  updateSearchInput(name, null)
                  setAddressHint(null)
                }} />
                주소 힌트 없이 검색
              </label>
            </fieldset>
          ) : null}

          <label className={flowStyles.selectField}>
            <span>검색할 상호명</span>
            <input value={name} onChange={(event) => {
              updateSearchInput(event.target.value, addressHint)
              setName(event.target.value)
            }} />
          </label>

          <Button
            disabled={searchPending || !name.trim()}
            onClick={submitPlaceSearch}
          >
            {searchPending ? '카카오 장소 검색 중…' : '카카오 장소 검색'}
          </Button>

          {visibleSearchView.status === 'error' ? <p className={styles.error} role="alert">{messageFor(visibleSearchView.error)}</p> : null}

          {visibleSearchView.status === 'success' ? (
            visibleSearchView.data.length ? (
              <ul className={styles.candidates}>
                {visibleSearchView.data.map((place, index) => (
                  <li key={`${place.kakaoPlaceUrl}-${index}`} className={styles.candidate}>
                    <h3>{place.placeName}</h3>
                    <p>{place.roadAddress}</p>
                    <p>{place.phoneNumber ?? '전화번호 정보 없음'}</p>
                    {place.district ? <p className={styles.meta}>{place.district}</p> : null}
                    <Button onClick={() => selectPlace(place)}>이 장소 선택</Button>
                  </li>
                ))}
              </ul>
            ) : (
              <p className={styles.hint}>검색 결과가 없습니다. 카카오 장소 URL을 직접 입력해 진행할 수 있습니다.</p>
            )
          ) : null}

          <Button variant="secondary" onClick={useManualPlaceEntry}>카카오 장소 URL 직접 입력</Button>
        </div>
      ) : null}

      {step === 'restaurant' ? (
        <div className={flowStyles.flow}>
          {placePrefill?.phoneNumberMissing ? <p className={styles.warning}>전화번호 정보가 없습니다. 직접 입력해 주세요.</p> : null}
          {manualPlaceEntry ? <p className={styles.hint}>검색 결과가 없어 카카오 장소 URL을 직접 입력합니다.</p> : null}
          <RegistrationFlow
            resourceName="맛집"
            previewPath="/api/admin/restaurant-registration-previews"
            createPath="/api/admin/restaurants"
            initialValues={{
              name,
              kakaoPlaceUrl: placePrefill?.kakaoPlaceUrl ?? '',
              roadAddress: placePrefill?.roadAddress ?? '',
              phoneNumber: placePrefill?.phoneNumber ?? '',
            }}
            inputs={[
              { name: 'name', label: '맛집 이름' },
              { name: 'kakaoPlaceUrl', label: '카카오 장소 URL', type: 'url' },
              { name: 'roadAddress', label: '도로명 주소' },
              { name: 'detailAddress', label: '상세 주소', required: false },
              { name: 'phoneNumber', label: '전화번호', type: 'tel' },
              { name: 'category', label: '음식 카테고리', options: CATEGORY_OPTIONS },
            ]}
            onCompleted={(id, kind) => {
              const transition = registrationCompletionTransition('restaurant', { status: 'success', resourceId: id, kind }, restaurantId !== null)
              if (transition) {
                setRestaurantId(transition.resourceId)
                setStep(transition.nextStep)
              }
            }}
          />
        </div>
      ) : null}

      {step === 'creator' ? (
        <RegistrationFlow
          resourceName="유튜버"
          previewPath="/api/admin/creator-registration-previews"
          createPath="/api/admin/creators"
          initialValues={{ channelUrl: channelUrlFromChannelId(detail.youtube.channelId) }}
          inputs={[{ name: 'channelUrl', label: '유튜브 채널 URL', type: 'url' }]}
          onCompleted={(id, kind) => {
            const transition = registrationCompletionTransition('creator', { status: 'success', resourceId: id, kind }, creatorId !== null)
            if (transition) {
              setCreatorId(transition.resourceId)
              setStep(transition.nextStep)
            }
          }}
        />
      ) : null}

      {step === 'video' ? (
        <RegistrationFlow
          resourceName="영상"
          previewPath="/api/admin/video-registration-previews"
          createPath="/api/admin/videos"
          initialValues={{ sourceUrl: detail.youtube.videoUrl.trim() }}
          inputs={[{ name: 'sourceUrl', label: '유튜브 영상 URL', type: 'url' }]}
          onCompleted={(id, kind) => {
            const transition = registrationCompletionTransition('video', { status: 'success', resourceId: id, kind }, videoId !== null)
            if (transition) {
              setVideoId(transition.resourceId)
              setStep(transition.nextStep)
            }
          }}
        />
      ) : null}

      {step === 'visit' ? (
        <div className={flowStyles.flow}>
          <h3>방문 관계 등록</h3>
          <p className={styles.hint}>AI가 제시한 방문 근거를 확인한 뒤 직접 체크해야 등록에 반영됩니다.</p>
          {visitEvidenceCandidates(detail.candidates).length ? (
            <ul className={styles.candidates}>
              {visitEvidenceCandidates(detail.candidates).map((item, index) => (
                <li key={`${item.value ?? ''}-${index}`} className={styles.candidate}>
                  <p>{item.value ?? '값 없음'}</p>
                  <p className={styles.meta}><StatusBadge tone="success">신뢰도 {(item.confidence * 100).toFixed(0)}%</StatusBadge></p>
                </li>
              ))}
            </ul>
          ) : (
            <p className={styles.warning}>방문 근거 후보가 없습니다. 직접 확인한 근거로만 진행해 주세요.</p>
          )}
          <label className={flowStyles.checkbox}>
            <input type="checkbox" checked={evidenceConfirmed} disabled={visitPending} onChange={(event) => setEvidenceConfirmed(event.target.checked)} />
            방문 근거를 확인했습니다.
          </label>
          {visitFieldErrors.visitEvidenceConfirmed ? <p className={styles.error}>{visitFieldErrors.visitEvidenceConfirmed}</p> : null}
          {visitError ? <p className={styles.error} role="alert">{visitError}</p> : null}
          <Button disabled={!evidenceConfirmed || visitPending} onClick={submitVisit}>
            {visitPending ? '등록 중…' : '방문 관계 등록'}
          </Button>
        </div>
      ) : null}

      {step === 'done' && visitResult ? (
        <section className={flowStyles.success} aria-live="polite">
          <h3>등록 완료</h3>
          <p>방문 관계 ID: {visitResult.id}</p>
        </section>
      ) : null}
    </section>
  )
}

function searchIdentity(sessionIdentity: string, seed: PlaceSearchSeed): string {
  return `${sessionIdentity}:${placeSearchSeedIdentity(seed)}`
}

function withoutIdentity(identities: Set<string>, identity: string): Set<string> {
  if (!identities.has(identity)) {
    return identities
  }
  const next = new Set(identities)
  next.delete(identity)
  return next
}
