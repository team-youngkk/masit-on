import type { FetchCreatorsResult } from '@/lib/restaurants-api'

export function findSelectedCreatorProfileImageUrl(
  creatorId: string | undefined,
  creatorsResult: FetchCreatorsResult,
): string | null {
  if (!creatorId || !creatorsResult.ok) {
    return null
  }

  const profileImageUrl = creatorsResult.data.items.find((creator) => creator.id === creatorId)?.profileImageUrl
  return isSafeHttpsUrl(profileImageUrl) ? profileImageUrl : null
}

export function isSafeHttpsUrl(value: unknown): value is string {
  if (typeof value !== 'string') {
    return false
  }

  try {
    const url = new URL(value)
    return url.protocol === 'https:' && url.hostname.length > 0 && !url.username && !url.password
  } catch {
    return false
  }
}
