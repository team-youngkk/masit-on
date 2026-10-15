import assert from 'node:assert/strict'
import test from 'node:test'

import { shouldSubmitFilterChange, submitFilterForm } from './filter-submit.ts'

test('자동 제출은 실제로 선택값이 달라졌을 때만 허용한다', () => {
  assert.equal(shouldSubmitFilterChange(true, '', '한식'), true)
  assert.equal(shouldSubmitFilterChange(true, '한식', ''), true)
  assert.equal(shouldSubmitFilterChange(true, '한식', '한식'), false)
  assert.equal(shouldSubmitFilterChange(false, '', '한식'), false)
})

test('폼을 찾으면 requestSubmit을 호출하고 폼이 아니면 아무 동작도 하지 않는다', () => {
  let submitCount = 0
  const documentRef = {
    getElementById: (id: string) =>
      id === 'structured-restaurant-search'
        ? { requestSubmit: () => { submitCount += 1 } }
        : null,
  }

  submitFilterForm(documentRef, 'structured-restaurant-search')
  submitFilterForm(documentRef, 'missing-form')

  assert.equal(submitCount, 1)
})
