export type CurationStatus = 'DRAFT' | 'PUBLISHED'

export type IdempotencyAttempt = { fingerprint: string; key: string }

export function idempotencyAttempt(
  previous: IdempotencyAttempt | null,
  fingerprint: string,
  generate: () => string,
): IdempotencyAttempt {
  return previous?.fingerprint === fingerprint ? previous : { fingerprint, key: generate() }
}

export function nextCurationPage(
  current: { status: CurationStatus | ''; page: number },
  change: Partial<{ status: CurationStatus | ''; page: number }>,
) {
  const next = { ...current, ...change }
  return { ...next, page: next.status !== current.status ? 1 : next.page }
}

export function validateCurationText(title: string, description: string): string[] {
  const errors: string[] = []
  const titleLength = Array.from(title.trim()).length
  const descriptionLength = Array.from(description.trim()).length
  if (titleLength < 1 || titleLength > 100) errors.push('제목은 공백을 제외하고 1~100자로 입력해 주세요.')
  if (descriptionLength > 1000) errors.push('설명은 공백을 제외하고 1000자 이하로 입력해 주세요.')
  return errors
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function parseRestaurantIds(value: string): { ids: string[]; errors: string[] } {
  const ids = value.split(/\r?\n/).map((id) => id.trim()).filter(Boolean)
  const errors: string[] = []
  if (ids.length > 20) errors.push('맛집은 최대 20개까지 구성할 수 있습니다.')
  if (new Set(ids).size !== ids.length) errors.push('중복된 맛집 UUID를 제거해 주세요.')
  if (ids.some((id) => !UUID.test(id))) errors.push('맛집 식별자는 UUID 형식이어야 합니다.')
  return { ids, errors }
}

export function moveItem<T>(items: readonly T[], index: number, offset: -1 | 1): T[] {
  const destination = index + offset
  if (index < 0 || index >= items.length || destination < 0 || destination >= items.length) return [...items]
  const next = [...items]
  ;[next[index], next[destination]] = [next[destination], next[index]]
  return next
}

export function validateMainOrder(selectedIds: readonly string[], publishedIds: readonly string[]): string[] {
  if (new Set(selectedIds).size !== selectedIds.length) return ['메인 순서에 중복된 큐레이션이 있습니다.']
  if (selectedIds.length > 5) return ['메인에는 최대 5개까지 게시할 수 있습니다.']
  if (selectedIds.length !== publishedIds.length
    || selectedIds.some((id) => !publishedIds.includes(id))) {
    return ['현재 게시 중인 큐레이션 전체를 빠짐없이 순서대로 선택해 주세요.']
  }
  return []
}
