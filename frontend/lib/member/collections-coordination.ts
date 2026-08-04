export type CollectionCreationAttempt = {
  normalizedName: string
  idempotencyKey: string
}

export function normalizeCollectionName(name: string): string {
  return name.trim()
}

export function collectionNameError(name: string): string | null {
  const normalized = normalizeCollectionName(name)
  const length = Array.from(normalized).length
  if (length === 0) return '컬렉션 이름을 입력해 주세요.'
  if (length > 50) return '컬렉션 이름은 50자 이하로 입력해 주세요.'
  return null
}

export function collectionAddErrorMessage(code: string | undefined): string {
  if (code === 'COLLECTION_RESTAURANT_LIMIT_EXCEEDED') {
    return '이 컬렉션에는 맛집을 최대 100곳까지 담을 수 있습니다.'
  }
  if (code === 'RESTAURANT_NOT_FOUND') {
    return '현재 공개된 맛집만 컬렉션에 담을 수 있습니다.'
  }
  return '맛집을 컬렉션에 담지 못했습니다. 다시 시도해 주세요.'
}

export function creationAttemptFor(
  current: CollectionCreationAttempt | null,
  name: string,
  createKey: () => string,
): CollectionCreationAttempt {
  const normalizedName = normalizeCollectionName(name)
  if (current?.normalizedName === normalizedName) return current
  return { normalizedName, idempotencyKey: createKey() }
}

export function positiveCollectionPage(value: string | undefined): number {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 1 ? parsed : 1
}

export function allowedCollectionPageSize(value: string | undefined): number {
  const parsed = Number(value)
  return parsed === 10 || parsed === 50 ? parsed : 20
}

export function previousCollectionPageAfterRemoval(
  currentPage: number,
  remainingItems: number,
): number | null {
  return currentPage > 1 && remainingItems === 0 ? currentPage - 1 : null
}
