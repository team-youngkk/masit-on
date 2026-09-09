import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  adminTagScope,
  appendTagDefinition,
  appendTagDefinitionToList,
  canExecuteTagMerge,
  mergeTargetOptions,
  prepareTagDefinition,
  selectCreatedTag,
  validateTagDefinition,
  validateTagEdit,
  withinAdminScope,
  type TagDefinition,
} from './visit-tags-coordination.ts'

const admin = { id: 'admin-a', role: 'ADMIN' }
const option = { code: 'MENU_NOODLES', displayName: '국수', type: 'MENU' }

test('익명·회원·복원 중에는 관리자 조회 범위를 생성하지 않는다', () => {
  assert.equal(adminTagScope('anonymous', null), null)
  assert.equal(adminTagScope('loading', admin), null)
  assert.equal(adminTagScope('authenticated', { id: 'member', role: 'MEMBER' }), null)
  assert.equal(adminTagScope('authenticated', admin), admin.id)
})

test('태그 전체 해제와 50개 저장은 허용하고 사유·비활성·중복·초과 입력은 거부한다', () => {
  const edit = { expectedVersion: 'opaque', tagCodes: [], reason: '영상 확인' }
  assert.equal(validateTagEdit(edit, [option]), null)
  assert.ok(validateTagEdit({ ...edit, reason: '  ' }, [option]))
  assert.ok(validateTagEdit({ ...edit, reason: '가'.repeat(1001) }, [option]))
  assert.ok(validateTagEdit({ ...edit, tagCodes: ['INACTIVE'] }, [option]))
  assert.equal(validateTagEdit({ ...edit, tagCodes: ['INACTIVE'] }, [option], ['INACTIVE']), null)
  assert.ok(validateTagEdit({ ...edit, tagCodes: [option.code, option.code] }, [option]))
  const options = Array.from({ length: 51 }, (_, index) => ({ ...option, code: `TAG_${index}` }))
  assert.equal(validateTagEdit({ ...edit, tagCodes: options.slice(0, 50).map(tag => tag.code) }, options), null)
  assert.ok(validateTagEdit({ ...edit, tagCodes: options.map(tag => tag.code) }, options))
})

test('요청 직전 계정이 달라지거나 MEMBER이면 관리자 API를 호출하지 않는다', async () => {
  let calls = 0
  for (const session of [null, { id: 'admin-b', role: 'ADMIN' }, { id: admin.id, role: 'MEMBER' }]) {
    await assert.rejects(withinAdminScope(admin.id, async () => session, async () => { calls++; return 'secret' }), { name: 'AbortError' })
  }
  assert.equal(calls, 0)
})

test('응답 본문을 읽는 동안 계정이 바뀌면 이전 관리자의 결과를 반환하지 않는다', async () => {
  let current = admin
  await assert.rejects(withinAdminScope(admin.id, async () => current, async () => {
    current = { id: 'admin-b', role: 'ADMIN' }
    return { privateTags: ['secret'] }
  }), { name: 'AbortError' })
})

test('로그아웃 후 같은 계정으로 돌아와도 취소된 이전 요청 결과는 폐기한다', async () => {
  const controller = new AbortController()
  await assert.rejects(withinAdminScope(admin.id, async () => admin, async () => {
    controller.abort()
    return 'old result'
  }, controller.signal), { name: 'AbortError' })
})

test('동일 ADMIN 세션의 요청 성공과 서버 오류를 호출자에게 전달한다', async () => {
  assert.equal(await withinAdminScope(admin.id, async () => admin, async () => 'tags'), 'tags')
  const conflict = new Error('VISIT_TAG_CONCURRENT_UPDATE')
  await assert.rejects(withinAdminScope(admin.id, async () => admin, async () => { throw conflict }), error => error === conflict)
})

const created: TagDefinition = {
  code: 'OCCASION_FAMILY',
  type: 'OCCASION',
  displayName: '가족 모임',
  aliases: ['가족식사'],
  status: 'ACTIVE',
  source: 'MANUAL_OVERRIDE',
  version: 0,
}

test('태그 생성 입력을 trim하고 줄 단위 별칭 배열로 변환한다', () => {
  assert.deepEqual(prepareTagDefinition({
    code: ' OCCASION_FAMILY ',
    type: 'OCCASION',
    displayName: ' 가족 모임 ',
    aliases: ' 가족식사 \n\n 가족 외식 ',
  }), {
    code: 'OCCASION_FAMILY',
    type: 'OCCASION',
    displayName: '가족 모임',
    aliases: ['가족식사', '가족 외식'],
  })
})

test('태그 생성의 코드 형식·유형 접두사·길이·정규화 중복을 검증한다', () => {
  assert.deepEqual(validateTagDefinition({ code: created.code, type: created.type, displayName: created.displayName, aliases: created.aliases }), {})
  assert.ok(validateTagDefinition({ code: 'occasion_family', type: 'OCCASION', displayName: created.displayName, aliases: [] }).code)
  assert.ok(validateTagDefinition({ code: 'MENU_FAMILY', type: 'OCCASION', displayName: created.displayName, aliases: [] }).code)
  assert.ok(validateTagDefinition({ code: created.code, type: created.type, displayName: '', aliases: [] }).displayName)
  assert.ok(validateTagDefinition({ code: created.code, type: created.type, displayName: 'ＡＢＣ', aliases: [' abc '] }).aliases)
  assert.ok(validateTagDefinition({ code: created.code, type: created.type, displayName: created.displayName, aliases: Array(21).fill('별칭') }).aliases)
})

test('생성 성공 시 두 목록 캐시에 한 번만 추가하고 편집 값과 사유를 보존해 선택한다', () => {
  const visitTags = { items: [], tagOptions: [option] }
  assert.deepEqual(appendTagDefinition(visitTags, created)?.tagOptions.map(tag => tag.code), [option.code, created.code])
  assert.strictEqual(appendTagDefinition({ items: [], tagOptions: [created] }, created)?.tagOptions[0], created)
  assert.deepEqual(appendTagDefinitionToList(undefined, created), { items: [created] })
  assert.deepEqual(appendTagDefinitionToList({ items: [created] }, created), { items: [created] })

  const edit = { expectedVersion: 'opaque', tagCodes: [option.code], reason: '영상 확인' }
  assert.deepEqual(selectCreatedTag(edit, created.code), { ...edit, tagCodes: [option.code, created.code] })
  assert.strictEqual(selectCreatedTag({ ...edit, tagCodes: [created.code] }, created.code).reason, edit.reason)
})

test('병합 대상은 활성 원본과 유형이 같고 자기 자신이 아닌 활성 태그만 허용한다', () => {
  const target = { ...created, code: 'OCCASION_GROUP', displayName: '단체 모임' }
  const wrongType = { ...created, code: 'MENU_GROUP', type: 'MENU' as const }
  const deprecated = { ...created, code: 'OCCASION_OLD', status: 'DEPRECATED' as const }

  assert.deepEqual(mergeTargetOptions(created, [created, target, wrongType, deprecated]), [target])
  assert.deepEqual(mergeTargetOptions(deprecated, [target]), [])
  assert.deepEqual(mergeTargetOptions(null, [target]), [])
})

test('현재 대상의 유효한 미리보기와 사유가 있고 요청 중이 아닐 때만 병합할 수 있다', () => {
  const preview = {
    source: created,
    target: { ...created, code: 'OCCASION_GROUP' },
    affectedVisitCount: 2,
    movedVisitTagCount: 1,
    deduplicatedVisitTagCount: 1,
    previewFingerprint: 'a'.repeat(64),
  }
  const input = { targetCode: preview.target.code, preview, previewStale: false, reason: '중복 정리', busy: false }

  assert.equal(canExecuteTagMerge(input), true)
  assert.equal(canExecuteTagMerge({ ...input, targetCode: 'OCCASION_OTHER' }), false)
  assert.equal(canExecuteTagMerge({ ...input, previewStale: true }), false)
  assert.equal(canExecuteTagMerge({ ...input, reason: ' ' }), false)
  assert.equal(canExecuteTagMerge({ ...input, reason: '가'.repeat(1001) }), false)
  assert.equal(canExecuteTagMerge({ ...input, busy: true }), false)
})
