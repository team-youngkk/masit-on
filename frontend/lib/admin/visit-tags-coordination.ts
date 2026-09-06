export type TagOption = { code: string; displayName: string; type: string }
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

export function adminTagScope(status: string, session: { id: string; role: string } | null): string | null {
  return status === 'authenticated' && session?.role === 'ADMIN' ? session.id : null
}

export function validateTagEdit(edit: TagEdit, options: TagOption[]): string | null {
  if (!edit.reason.trim() || edit.reason.trim().length > 1000) return '수정 사유를 1~1000자로 입력해 주세요.'
  if (edit.tagCodes.length > 50) return '태그는 최대 50개까지 선택할 수 있습니다.'
  const active = new Set(options.map(tag => tag.code))
  if (new Set(edit.tagCodes).size !== edit.tagCodes.length || edit.tagCodes.some(code => !active.has(code))) {
    return '비활성 태그를 해제하고 현재 선택 가능한 태그만 저장해 주세요.'
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
