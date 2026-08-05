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

export function parseRestaurantIds(value: string): { ids: string[]; errors: string[] } {
  const ids = value.split(/\r?\n/).map((id) => id.trim()).filter(Boolean)
  const errors: string[] = []
  if (ids.length > 20) errors.push('맛집은 최대 20개까지 구성할 수 있습니다.')
  if (new Set(ids).size !== ids.length) errors.push('중복된 맛집 식별자를 제거해 주세요.')
  return { ids, errors }
}

export function moveItem<T>(items: readonly T[], index: number, offset: -1 | 1): T[] {
  const destination = index + offset
  if (index < 0 || index >= items.length || destination < 0 || destination >= items.length) return [...items]
  const next = [...items]
  ;[next[index], next[destination]] = [next[destination], next[index]]
  return next
}
