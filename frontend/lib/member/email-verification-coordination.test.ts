const emailVerificationAssert = require('node:assert/strict')
const emailVerificationTest = require('node:test')
const {
  createEmailVerificationSingleFlight,
  resendEmailVerification,
  submitEmailVerification,
} = require('./email-verification-coordination.ts')

const createDeferred = (): { promise: Promise<void>; resolve: () => void } => {
  let resolve!: () => void
  const promise = new Promise<void>((next) => {
    resolve = () => next()
  })
  return { promise, resolve }
}

emailVerificationTest('빠른 두 요청에서 action은 한 번만 시작되고 종료 후 다시 실행할 수 있다', async () => {
  const guard = createEmailVerificationSingleFlight()
  const release = createDeferred()
  let started = 0

  const firstRun = guard.run(async () => {
    started += 1
    await release.promise
    return started
  })
  const secondRun = guard.run(async () => {
    started += 1
    return started
  })

  emailVerificationAssert.equal(started, 1)

  release.resolve()
  const [firstResult, secondResult] = await Promise.all([firstRun, secondRun])
  const retryResult = await guard.run(async () => {
    started += 1
    return started
  })

  emailVerificationAssert.equal(firstResult, 1)
  emailVerificationAssert.equal(secondResult, 1)
  emailVerificationAssert.equal(retryResult, 2)
  emailVerificationAssert.equal(started, 2)
})

emailVerificationTest('인증 성공 시 성공 상태를 반환하고 토큰을 정리한다', async () => {
  const tokens: string[] = []
  let clearCount = 0

  const result = await submitEmailVerification({
    token: 'opaque-token',
    verify: async (token: string) => {
      tokens.push(token)
    },
    clearToken: () => {
      clearCount += 1
    },
  })

  emailVerificationAssert.deepEqual(tokens, ['opaque-token'])
  emailVerificationAssert.equal(clearCount, 1)
  emailVerificationAssert.deepEqual(result, {
    verified: true,
    shouldOfferResend: false,
    feedback: {
      kind: 'status',
      text: '이메일 인증이 완료되었습니다. 자동 로그인되지 않으므로 로그인 화면에서 다시 로그인해 주세요.',
    },
  })
})

emailVerificationTest('400 인증 실패 시 토큰을 정리하고 재발송을 안내한다', async () => {
  let clearCount = 0

  const result = await submitEmailVerification({
    token: 'opaque-token',
    verify: async () => {
      throw new Response(null, { status: 400 })
    },
    clearToken: () => {
      clearCount += 1
    },
  })

  emailVerificationAssert.equal(clearCount, 1)
  emailVerificationAssert.deepEqual(result, {
    verified: false,
    shouldOfferResend: true,
    feedback: {
      kind: 'alert',
      text: '이메일 인증을 완료하지 못했습니다. 토큰을 다시 확인하거나 아래에서 인증 메일을 다시 요청해 주세요.',
    },
  })
})

emailVerificationTest('503 인증 장애 시 토큰을 보존하고 재시도를 안내한다', async () => {
  let clearCount = 0

  const result = await submitEmailVerification({
    token: 'opaque-token',
    verify: async () => {
      throw new Response(null, { status: 503 })
    },
    clearToken: () => {
      clearCount += 1
    },
  })

  emailVerificationAssert.equal(clearCount, 0)
  emailVerificationAssert.deepEqual(result, {
    verified: false,
    shouldOfferResend: false,
    feedback: {
      kind: 'alert',
      text: '이메일 인증 요청을 처리하지 못했습니다. 입력한 토큰을 유지했으니 잠시 후 다시 시도해 주세요.',
    },
  })
})

emailVerificationTest('네트워크 오류 시 토큰을 보존하고 재시도를 안내한다', async () => {
  let clearCount = 0

  const result = await submitEmailVerification({
    token: 'opaque-token',
    verify: async () => {
      throw new TypeError('network unavailable')
    },
    clearToken: () => {
      clearCount += 1
    },
  })

  emailVerificationAssert.equal(clearCount, 0)
  emailVerificationAssert.equal(result.verified, false)
  emailVerificationAssert.equal(result.shouldOfferResend, false)
  emailVerificationAssert.equal(result.feedback.kind, 'alert')
})

emailVerificationTest('재발송 성공 시 공통 접수 안내를 반환한다', async () => {
  const emails: string[] = []

  const result = await resendEmailVerification({
    email: 'member@example.com',
    resend: async (email: string) => {
      emails.push(email)
    },
  })

  emailVerificationAssert.deepEqual(emails, ['member@example.com'])
  emailVerificationAssert.deepEqual(result, {
    feedback: {
      kind: 'status',
      text: '인증 메일 재발송 요청을 접수했습니다. 계정 상태나 실제 발송 여부와 관계없이 같은 안내를 제공합니다.',
    },
  })
})

emailVerificationTest('재발송 실패 시 재시도 안내를 반환한다', async () => {
  const result = await resendEmailVerification({
    email: 'member@example.com',
    resend: async () => {
      throw new Error('failed')
    },
  })

  emailVerificationAssert.deepEqual(result, {
    feedback: {
      kind: 'alert',
      text: '인증 메일 재발송 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
    },
  })
})
