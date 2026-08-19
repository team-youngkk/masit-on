import assert from 'node:assert/strict'
import test from 'node:test'

import {
  CreatorListError,
  fetchCreatorReferences,
} from './creators-api.ts'

test('정상 응답에서 유효한 유튜버 항목만 원문 문자열 그대로 반환한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json({
      items: [
        { id: 'opaque/id?creator=1', channelName: '  맛집 채널  ' },
        null,
        [],
        { id: 'missing-channel-name' },
        { id: 'non-string-channel-name', channelName: 42 },
        { id: 7, channelName: '문자열 채널' },
        { id: 'valid-id', channelName: '유효한 채널' },
      ],
    }),
  )

  const result = await fetchCreatorReferences()

  assert.deepEqual(result, [
    { id: 'opaque/id?creator=1', channelName: '  맛집 채널  ' },
    { id: 'valid-id', channelName: '유효한 채널' },
  ])
})

test('배열이 아닌 items 응답은 CreatorListError로 변환한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ items: null }))

  await assert.rejects(
    fetchCreatorReferences(),
    (error: unknown) => error instanceof CreatorListError,
  )
})

test('네트워크 오류는 CreatorListError로 변환한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => {
    throw new TypeError('network down')
  })

  await assert.rejects(
    fetchCreatorReferences(),
    (error: unknown) =>
      error instanceof CreatorListError
      && error.traceId === undefined,
  )
})

test('비정상 HTTP 응답의 traceId를 CreatorListError에 보존한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json(
      { code: 'INTERNAL_ERROR', traceId: 'trace-creator-list' },
      { status: 500 },
    ),
  )

  await assert.rejects(
    fetchCreatorReferences(),
    (error: unknown) =>
      error instanceof CreatorListError
      && error.traceId === 'trace-creator-list',
  )
})

test('JSON 형식이 잘못된 정상 응답은 CreatorListError로 변환한다', async (t) => {
  t.mock.method(globalThis, 'fetch', async () =>
    new Response('{', { headers: { 'content-type': 'application/json' } }),
  )

  await assert.rejects(
    fetchCreatorReferences(),
    (error: unknown) => error instanceof CreatorListError,
  )
})
