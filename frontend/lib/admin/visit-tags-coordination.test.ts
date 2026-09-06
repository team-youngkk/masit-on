import assert from 'node:assert/strict'
import { test } from 'node:test'
import { adminTagScope, validateTagEdit, withinAdminScope } from './visit-tags-coordination.ts'

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
