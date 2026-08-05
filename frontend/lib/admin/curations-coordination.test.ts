import assert from 'node:assert/strict'
import test from 'node:test'

import { idempotencyAttempt, moveItem, nextCurationPage, parseRestaurantIds, validateCurationText, validateMainOrder } from './curations-coordination.ts'

test('상태 필터를 바꾸면 첫 페이지로 돌아간다', () => {
  assert.deepEqual(nextCurationPage({ status: '', page: 4 }, { status: 'DRAFT' }), { status: 'DRAFT', page: 1 })
  assert.deepEqual(nextCurationPage({ status: 'DRAFT', page: 2 }, { page: 3 }), { status: 'DRAFT', page: 3 })
})

test('제목과 설명을 유니코드 코드 포인트 기준으로 검증한다', () => {
  assert.deepEqual(validateCurationText(' ', ''), ['제목은 공백을 제외하고 1~100자로 입력해 주세요.'])
  assert.deepEqual(validateCurationText('😀'.repeat(100), '가'.repeat(1000)), [])
  assert.equal(validateCurationText('😀'.repeat(101), '')[0], '제목은 공백을 제외하고 1~100자로 입력해 주세요.')
})

test('맛집 UUID의 중복과 형식 및 20개 상한을 검증한다', () => {
  const id = '123e4567-e89b-42d3-a456-426614174000'
  assert.deepEqual(parseRestaurantIds(`${id}\n${id}`).errors, ['중복된 맛집 UUID를 제거해 주세요.'])
  assert.deepEqual(parseRestaurantIds('not-an-id').errors, ['맛집 식별자는 UUID 형식이어야 합니다.'])
  assert.equal(parseRestaurantIds(Array.from({ length: 21 }, (_, i) => `123e4567-e89b-42d3-a456-${String(i).padStart(12, '0')}`).join('\n')).errors[0], '맛집은 최대 20개까지 구성할 수 있습니다.')
})

test('순서 이동과 게시 큐레이션 전체 교체 조건을 지킨다', () => {
  assert.deepEqual(moveItem(['a', 'b', 'c'], 1, -1), ['b', 'a', 'c'])
  assert.deepEqual(moveItem(['a', 'b'], 0, -1), ['a', 'b'])
  assert.deepEqual(validateMainOrder(['b', 'a'], ['a', 'b']), [])
  assert.equal(validateMainOrder(['a'], ['a', 'b'])[0], '현재 게시 중인 큐레이션 전체를 빠짐없이 순서대로 선택해 주세요.')
})

test('같은 생성 본문 재시도는 동일한 멱등 키를 사용한다', () => {
  let sequence = 0
  const first = idempotencyAttempt(null, '제목\n설명', () => `key-${++sequence}`)
  const retry = idempotencyAttempt(first, '제목\n설명', () => `key-${++sequence}`)
  const changed = idempotencyAttempt(retry, '다른 제목\n설명', () => `key-${++sequence}`)
  assert.equal(retry.key, first.key)
  assert.notEqual(changed.key, first.key)
})
