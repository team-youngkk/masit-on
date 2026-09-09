export const TAG_DEFINITION_TYPES = ['MENU', 'TASTE', 'OCCASION', 'ATMOSPHERE'] as const

export type TagDefinitionType = typeof TAG_DEFINITION_TYPES[number]
export type TagOption = { code: string; displayName: string; type: string }
export type TagDefinition = TagOption & {
  type: TagDefinitionType
  aliases: string[]
  status: 'ACTIVE' | 'DEPRECATED'
  source: string
  version: number
}
export type TagDefinitionList = { items: TagDefinition[] }
export type Page = { number: number; size: number; totalElements: number; totalPages: number; hasNext: boolean }
export type TagDefinitionManagement = { items: TagDefinition[]; page: Page }
export type TagDefinitionAudit = { id: string; action: string; before: TagDefinition; after: TagDefinition; reason: string; changedByMemberId: string | null; changedAt: string; version: number }
export type TagDefinitionHistory = { items: TagDefinitionAudit[]; page: Page }
export type TagDefinitionDraft = {
  code: string
  type: TagDefinitionType
  displayName: string
  aliases: string
}
export type CreateTagDefinitionRequest = Omit<TagDefinitionDraft, 'aliases'> & { aliases: string[] }
export type VisitTags = {
  visitId: string
  creatorName: string
  videoTitle: string
  videoUrl: string
  version: string
  tags: (TagOption & { source: string })[]
}
export type RestaurantVisitTags = { items: VisitTags[]; tagOptions: TagOption[] }
export type TagEdit = { expectedVersion: string; tagCodes: string[]; reason: string }

const TAG_CODE = /^(MENU|TASTE|OCCASION|ATMOSPHERE)_[A-Z0-9]+(?:_[A-Z0-9]+)*$/

export function prepareTagDefinition(draft: TagDefinitionDraft): CreateTagDefinitionRequest {
  return {
    code: draft.code.trim(),
    type: draft.type,
    displayName: draft.displayName.trim(),
    aliases: draft.aliases.split(/\r?\n/u).map(alias => alias.trim()).filter(Boolean),
  }
}

export function validateTagDefinition(request: CreateTagDefinitionRequest): Record<string, string> {
  const errors: Record<string, string> = {}
  if (request.code.length < 3 || request.code.length > 64 || !TAG_CODE.test(request.code)) {
    errors.code = '유형 접두사와 영문 대문자, 숫자, 밑줄을 사용해 3~64자로 입력해 주세요.'
  } else if (!request.code.startsWith(`${request.type}_`)) {
    errors.code = `코드는 ${request.type}_로 시작해야 합니다.`
  }
  if (!request.displayName || request.displayName.length > 100) errors.displayName = '표시명을 1~100자로 입력해 주세요.'
  if (request.aliases.length > 20 || request.aliases.some(alias => alias.length > 100)) {
    errors.aliases = '별칭을 줄마다 하나씩 최대 20개, 각각 100자 이내로 입력해 주세요.'
  }
  const normalized = [request.displayName, ...request.aliases]
    .map(term => term.normalize('NFKC').trim().replace(/\s+/gu, ' ').toLowerCase())
  if (normalized.some((term, index) => term && normalized.indexOf(term) !== index)) {
    errors.aliases = '표시명과 별칭은 정규화했을 때 서로 달라야 합니다.'
  }
  return errors
}

export function appendTagDefinition(
  current: RestaurantVisitTags | undefined,
  definition: TagDefinition,
): RestaurantVisitTags | undefined {
  if (!current || current.tagOptions.some(option => option.code === definition.code)) return current
  return { ...current, tagOptions: [...current.tagOptions, definition] }
}

export function appendTagDefinitionToList(
  current: TagDefinitionList | undefined,
  definition: TagDefinition,
): TagDefinitionList {
  const items = current?.items ?? []
  return items.some(item => item.code === definition.code) ? { items } : { items: [...items, definition] }
}

export function selectCreatedTag(edit: TagEdit, code: string): TagEdit {
  return edit.tagCodes.includes(code) ? edit : { ...edit, tagCodes: [...edit.tagCodes, code] }
}

export function adminTagScope(status: string, session: { id: string; role: string } | null): string | null {
  return status === 'authenticated' && session?.role === 'ADMIN' ? session.id : null
}

export function validateTagEdit(edit: TagEdit, options: TagOption[], existingCodes: string[] = []): string | null {
  if (!edit.reason.trim() || edit.reason.trim().length > 1000) return '수정 사유를 1~1000자로 입력해 주세요.'
  if (edit.tagCodes.length > 50) return '태그는 최대 50개까지 선택할 수 있습니다.'
  const active = new Set(options.map(tag => tag.code))
  const existing = new Set(existingCodes)
  if (new Set(edit.tagCodes).size !== edit.tagCodes.length || edit.tagCodes.some(code => !active.has(code) && !existing.has(code))) {
    return '새로 선택하는 태그는 활성 상태여야 합니다.'
  }
  return null
}

// 응답 본문을 읽는 동안에도 계정이 바뀔 수 있으므로 요청 전후 모두 검사한다.
export async function withinAdminScope<T>(
  accountId: string,
  session: () => Promise<{ id: string; role: string } | null>,
  request: () => Promise<T>,
  signal?: AbortSignal,
): Promise<T> {
  const assertScope = async () => {
    signal?.throwIfAborted()
    const current = await session()
    signal?.throwIfAborted()
    if (current?.id !== accountId || current.role !== 'ADMIN') throw new DOMException('관리자 세션이 변경되었습니다.', 'AbortError')
  }
  await assertScope()
  const result = await request()
  await assertScope()
  return result
}
