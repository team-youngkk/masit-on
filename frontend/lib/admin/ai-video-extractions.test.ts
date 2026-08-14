import assert from 'node:assert/strict'
import test from 'node:test'

import { clearAccessToken, login } from './auth.ts'
import { createAiVideoExtraction, aiExtractionMessageFor } from './ai-video-extractions.ts'

const job = (reused: boolean) => ({
  jobId: reused ? 'job-reused' : 'job-new',
  source: 'ADMIN',
  youtube: { channelId: 'channel-id', videoId: 'video-id', videoUrl: 'https://www.youtube.com/watch?v=video-id' },
  executionStatus: 'QUEUED',
  resultCompleteness: null,
  reviewStatus: null,
  provider: 'GOOGLE_GEMINI',
  modelVersion: 'gemini-3.5-flash-lite',
  promptVersion: 'P1',
  schemaVersion: 'S1',
  attemptCount: 0,
  createdAt: '2026-08-12T00:00:00Z',
  startedAt: null,
  finishedAt: null,
  reused,
})

test('신규 접수 API는 trim된 입력과 멱등 키를 JSON 본문으로 보내고 reused를 보존한다', async () => {
  const previousFetch = globalThis.fetch
  const requestBodies: unknown[] = []
  const responses = [job(false), job(true)]
  let call = 0
  globalThis.fetch = async (_input, init) => {
    if (call++ === 0) return new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresInSeconds: 3600 }), { status: 200 })
    requestBodies.push(JSON.parse(String(init?.body)))
    return new Response(JSON.stringify(responses.shift()), { status: 200 })
  }

  try {
    clearAccessToken()
    await login('admin', 'password')
    const created = await createAiVideoExtraction('  https://youtu.be/video-id  ', '  보완 메모  ', 'key-1')
    const reused = await createAiVideoExtraction('https://youtu.be/video-id', '보완 메모', 'key-1')

    assert.equal(created.reused, false)
    assert.equal(reused.reused, true)
    assert.deepEqual(requestBodies, [
      { videoUrl: 'https://youtu.be/video-id', supplementText: '보완 메모', idempotencyKey: 'key-1' },
      { videoUrl: 'https://youtu.be/video-id', supplementText: '보완 메모', idempotencyKey: 'key-1' },
    ])
  } finally {
    clearAccessToken()
    globalThis.fetch = previousFetch
  }
})

test('빈 보완 텍스트는 신규 접수 요청에서 생략한다', async () => {
  const previousFetch = globalThis.fetch
  const requestBodies: unknown[] = []
  let call = 0
  globalThis.fetch = async (_input, init) => {
    if (call++ === 0) return new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresInSeconds: 3600 }), { status: 200 })
    requestBodies.push(JSON.parse(String(init?.body)))
    return new Response(JSON.stringify(job(false)), { status: 202 })
  }

  try {
    clearAccessToken()
    await login('admin', 'password')
    await createAiVideoExtraction('https://youtu.be/video-id', '   ', 'key-2')
    assert.deepEqual(requestBodies, [{ videoUrl: 'https://youtu.be/video-id', idempotencyKey: 'key-2' }])
  } finally {
    clearAccessToken()
    globalThis.fetch = previousFetch
  }
})

test('신규 접수 API 오류는 계약 코드별 안전한 안내로 변환한다', async () => {
  const previousFetch = globalThis.fetch
  let call = 0
  globalThis.fetch = async () => {
    if (call++ === 0) return new Response(JSON.stringify({ accessToken: 'test-token', tokenType: 'Bearer', expiresInSeconds: 3600 }), { status: 200 })
    return new Response(JSON.stringify({ code: 'AIEXTRACT_INVALID_VIDEO_URL', message: 'internal detail', traceId: 'trace-1' }), { status: 400 })
  }

  try {
    clearAccessToken()
    await login('admin', 'password')
    let reason: unknown
    try {
      await createAiVideoExtraction('invalid', '', 'key-3')
      assert.fail('잘못된 URL 접수는 실패해야 합니다.')
    } catch (caught) {
      reason = caught
    }
    assert.equal(aiExtractionMessageFor(reason, 'submission'), '공개 YouTube 영상 URL을 확인해 주세요.')
  } finally {
    clearAccessToken()
    globalThis.fetch = previousFetch
  }
})
