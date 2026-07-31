import assert from 'node:assert/strict'
import test from 'node:test'

import { trustedClientForwardingHeaders } from './trusted-client-forwarding.ts'

test('서로 다른 클라이언트 주소는 각각의 백엔드 전달 헤더로 분리한다', () => {
  const first = trustedClientForwardingHeaders('203.0.113.10')
  const second = trustedClientForwardingHeaders('203.0.113.11')

  assert.deepEqual(first, { 'X-Forwarded-For': '203.0.113.10' })
  assert.deepEqual(second, { 'X-Forwarded-For': '203.0.113.11' })
})

test('신뢰 가능한 클라이언트 주소가 없으면 전달 헤더를 만들지 않는다', () => {
  assert.equal(trustedClientForwardingHeaders(), undefined)
})
