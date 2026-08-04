import assert from 'node:assert/strict'
import test from 'node:test'

import {
  allowedCollectionPageSize,
  collectionAddErrorMessage,
  collectionNameError,
  creationAttemptFor,
  positiveCollectionPage,
  previousCollectionPageAfterRemoval,
} from './collections-coordination.ts'

test('같은 이름의 생성 재시도는 동일한 멱등 키를 유지한다', () => {
  let sequence = 0
  const createKey = () => `key-${++sequence}`
  const first = creationAttemptFor(null, ' 가족과 갈 곳 ', createKey)
  const retried = creationAttemptFor(first, '가족과 갈 곳', createKey)
  const changed = creationAttemptFor(retried, '데이트', createKey)

  assert.equal(first.idempotencyKey, 'key-1')
  assert.strictEqual(retried, first)
  assert.equal(changed.idempotencyKey, 'key-2')
})

test('마지막 맛집을 제거해 현재 페이지가 비면 이전 페이지로 이동한다', () => {
  assert.equal(previousCollectionPageAfterRemoval(3, 0), 2)
  assert.equal(previousCollectionPageAfterRemoval(1, 0), null)
  assert.equal(previousCollectionPageAfterRemoval(3, 1), null)
})

test('컬렉션 이름은 공백 제거 후 1자에서 50자만 허용한다', () => {
  assert.equal(collectionNameError('   '), '컬렉션 이름을 입력해 주세요.')
  assert.equal(collectionNameError('가'.repeat(51)), '컬렉션 이름은 50자 이하로 입력해 주세요.')
  assert.equal(collectionNameError(` ${'가'.repeat(50)} `), null)
})

test('컬렉션 이름은 유니코드 코드 포인트 기준으로 50자를 허용한다', () => {
  assert.equal(collectionNameError('😀'.repeat(50)), null)
  assert.notEqual(collectionNameError('😀'.repeat(51)), null)
})

test('맛집 추가 오류는 상한과 공개 상태에 맞는 다음 행동을 안내한다', () => {
  assert.match(collectionAddErrorMessage('COLLECTION_RESTAURANT_LIMIT_EXCEEDED'), /100/)
  assert.match(collectionAddErrorMessage('RESTAURANT_NOT_FOUND'), /공개/)
  assert.match(collectionAddErrorMessage(undefined), /다시 시도/)
})

test('상세 페이지는 1-base와 허용 크기만 사용한다', () => {
  assert.equal(positiveCollectionPage('0'), 1)
  assert.equal(positiveCollectionPage('3'), 3)
  assert.equal(allowedCollectionPageSize('10'), 10)
  assert.equal(allowedCollectionPageSize('50'), 50)
  assert.equal(allowedCollectionPageSize('30'), 20)
})
