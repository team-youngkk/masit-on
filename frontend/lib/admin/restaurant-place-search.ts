'use client'

import { adminJson } from './api.ts'

export type AdminPlaceSearchResult = {
  placeName: string
  kakaoPlaceUrl: string
  roadAddress: string
  phoneNumber: string | null
  district: string | null
}

export async function searchAdminPlaceCandidates(
  name: string,
  roadAddressHint: string | null,
): Promise<AdminPlaceSearchResult[]> {
  const normalizedName = name.trim()
  const normalizedHint = roadAddressHint?.trim() || null
  const raw = await adminJson<RawAdminPlaceSearchResponse>('/api/admin/restaurant-place-searches', {
    method: 'POST',
    body: JSON.stringify({ name: normalizedName, roadAddressHint: normalizedHint }),
  })
  return normalizePlaceSearchResponse(raw)
}

type RawAdminPlaceSearchResponse = { items: unknown }

export function normalizePlaceSearchResponse(raw: RawAdminPlaceSearchResponse): AdminPlaceSearchResult[] {
  if (!Array.isArray(raw.items)) {
    return []
  }

  return raw.items
    .map(normalizePlaceSearchResult)
    .filter((value): value is AdminPlaceSearchResult => value !== null)
}

function normalizePlaceSearchResult(value: unknown): AdminPlaceSearchResult | null {
  const raw = record(value)
  if (typeof raw.placeName !== 'string' || typeof raw.kakaoPlaceUrl !== 'string' || typeof raw.roadAddress !== 'string') {
    return null
  }

  const placeName = raw.placeName.trim()
  const kakaoPlaceUrl = raw.kakaoPlaceUrl.trim()
  const roadAddress = raw.roadAddress.trim()
  if (!placeName || !kakaoPlaceUrl || !roadAddress) {
    return null
  }

  return {
    placeName,
    kakaoPlaceUrl,
    roadAddress,
    phoneNumber: typeof raw.phoneNumber === 'string' ? raw.phoneNumber.trim() || null : null,
    district: typeof raw.district === 'string' ? raw.district.trim() || null : null,
  }
}

function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : {}
}
