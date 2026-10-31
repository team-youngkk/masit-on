import assert from 'node:assert/strict'
import test from 'node:test'

import { aiDiagnosticAction, aiDiagnosticLabel, parseAiDiagnostic } from './ai-diagnostics.ts'

test('403 응답은 접근 거부로 표시하고 결제 원인으로 단정하지 않는다', () => {
  const diagnostic = parseAiDiagnostic({ version: 1, reasonCode: 'PROVIDER_ACCESS_DENIED', service: 'GEMINI', stage: 'PROVIDER_REQUEST', httpStatus: 403, providerCode: 'PERMISSION_DENIED', missingFields: [] })
  assert.equal(aiDiagnosticLabel(diagnostic), 'Gemini 접근이 거부됐습니다. · HTTP 403 · PERMISSION_DENIED')
  assert.match(aiDiagnosticAction(diagnostic) ?? '', /권한/)
})

test('필수 누락 필드는 해당 진단 객체에서만 표시한다', () => {
  const first = parseAiDiagnostic({ version: 1, reasonCode: 'MISSING_REQUIRED_FIELD', missingFields: ['address', 'location', 'address'] })
  const second = parseAiDiagnostic({ version: 1, reasonCode: 'MISSING_REQUIRED_FIELD', missingFields: [] })
  assert.equal(aiDiagnosticLabel(first), '주소·위치 후보가 없습니다.')
  assert.equal(aiDiagnosticLabel(second), '등록에 필요한 필수 후보가 없습니다.')
})

test('과거 오류와 알 수 없는 진단 코드는 추정하지 않는다', () => {
  assert.match(aiDiagnosticLabel(null, 'UPSTREAM') ?? '', /세부 원인/)
  assert.match(aiDiagnosticLabel(parseAiDiagnostic({ version: 1, reasonCode: 'NEW_REASON' })) ?? '', /지원되지 않는/)
  assert.equal(parseAiDiagnostic({ version: 2, reasonCode: 'PROVIDER_PAYMENT_REQUIRED' }), null)
})
