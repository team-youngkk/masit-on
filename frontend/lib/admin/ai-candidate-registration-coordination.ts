import type { AiCandidate } from './ai-video-extractions.ts'

export function isCurrentAsyncRequest(
  request: { generation: number; identity: string },
  currentGeneration: number,
  currentIdentity: string,
): boolean {
  return request.generation === currentGeneration && request.identity === currentIdentity
}

export function restaurantNameCandidates(candidates: AiCandidate[]): AiCandidate[] {
  return candidates.filter((candidate) => candidate.field === 'restaurantName')
}

export function addressCandidates(candidates: AiCandidate[]): AiCandidate[] {
  return candidates.filter((candidate) => candidate.field === 'address')
}

export function visitEvidenceCandidates(candidates: AiCandidate[]): AiCandidate[] {
  return candidates.filter((candidate) => candidate.field === 'visitEvidence')
}

export function placeSearchSeed(
  restaurantName: string,
  addressHint: string | null,
): { name: string; roadAddressHint: string | null } {
  const normalizedHint = addressHint?.trim()
  return { name: restaurantName.trim(), roadAddressHint: normalizedHint ? normalizedHint : null }
}

export function placeSearchSeedIdentity(seed: ReturnType<typeof placeSearchSeed>): string {
  return JSON.stringify([seed.name, seed.roadAddressHint])
}

export type PlacePrefill = {
  kakaoPlaceUrl: string
  roadAddress: string
  phoneNumber: string
  phoneNumberMissing: boolean
}

export function prefillFromPlace(place: {
  kakaoPlaceUrl: string
  roadAddress: string
  phoneNumber: string | null
}): PlacePrefill {
  return {
    kakaoPlaceUrl: place.kakaoPlaceUrl.trim(),
    roadAddress: place.roadAddress.trim(),
    phoneNumber: place.phoneNumber?.trim() ?? '',
    phoneNumberMissing: place.phoneNumber === null,
  }
}

export function channelUrlFromChannelId(channelId: string): string {
  return `https://www.youtube.com/channel/${encodeURIComponent(channelId.trim())}`
}

export type VisitRelationshipPayload = {
  restaurantId: string
  creatorId: string
  videoId: string
  visitEvidenceConfirmed: boolean
}

/**
 * visitEvidenceConfirmed는 관리자가 화면에서 직접 확인 체크를 해야만 true가 된다.
 * AI 후보 값을 그대로 옮기지 않기 위해 boolean === true인 경우만 통과시킨다.
 */
export function buildVisitRelationshipPayload(
  restaurantId: string,
  creatorId: string,
  videoId: string,
  adminConfirmedEvidence: boolean,
): VisitRelationshipPayload {
  return {
    restaurantId: restaurantId.trim(),
    creatorId: creatorId.trim(),
    videoId: videoId.trim(),
    visitEvidenceConfirmed: adminConfirmedEvidence === true,
  }
}
