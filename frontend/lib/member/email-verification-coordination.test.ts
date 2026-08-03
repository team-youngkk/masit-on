const emailVerificationAssert = require('node:assert/strict')
const emailVerificationTest = require('node:test')
const {
  createEmailVerificationSingleFlight,
  normalizeEmailVerificationCodeInput,
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

emailVerificationTest('single flight reuses the active verification request', async () => {
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

emailVerificationTest('verification code input trims ASCII edge whitespace and uppercases without truncating', () => {
  emailVerificationAssert.equal(
    normalizeEmailVerificationCodeInput(' AB7K9M2Q '),
    'AB7K9M2Q',
  )
  emailVerificationAssert.equal(
    normalizeEmailVerificationCodeInput(' \t ab12cd34 \n'),
    'AB12CD34',
  )
  emailVerificationAssert.equal(
    normalizeEmailVerificationCodeInput('abcdefghi'),
    'ABCDEFGHI',
  )
  emailVerificationAssert.equal(
    normalizeEmailVerificationCodeInput('ab cd1234'),
    'AB CD1234',
  )
})

emailVerificationTest('successful verification clears the code and returns success feedback', async () => {
  const tokens: string[] = []
  let clearCount = 0

  const result = await submitEmailVerification({
    token: 'AB12CD34',
    verify: async (token: string) => {
      tokens.push(token)
    },
    clearToken: () => {
      clearCount += 1
    },
  })

  emailVerificationAssert.deepEqual(tokens, ['AB12CD34'])
  emailVerificationAssert.equal(clearCount, 1)
  emailVerificationAssert.deepEqual(result, {
    verified: true,
    shouldOfferResend: false,
    feedback: {
      kind: 'status',
      text: '이메일 인증이 완료되었습니다. 로그인 화면에서 다시 로그인해 주세요.',
    },
  })
})

emailVerificationTest('400 verification failure clears the code and offers resend', async () => {
  let clearCount = 0

  const result = await submitEmailVerification({
    token: 'AB12CD34',
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
      text: '8자 인증 코드를 확인하거나 아래에서 인증 메일을 다시 요청해 주세요.',
    },
  })
})

emailVerificationTest('429 verification throttling preserves the code and keeps resend hidden', async () => {
  let clearCount = 0

  const result = await submitEmailVerification({
    token: 'AB12CD34',
    verify: async () => {
      throw new Response(null, { status: 429 })
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
      text: '인증 요청을 처리하지 못했습니다. 입력한 8자 코드를 유지했으니 잠시 후 다시 시도해 주세요.',
    },
  })
})

emailVerificationTest('503 verification outage preserves the code and keeps resend hidden', async () => {
  let clearCount = 0

  const result = await submitEmailVerification({
    token: 'AB12CD34',
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
      text: '인증 요청을 처리하지 못했습니다. 입력한 8자 코드를 유지했으니 잠시 후 다시 시도해 주세요.',
    },
  })
})

emailVerificationTest('network failures preserve the code and keep resend hidden', async () => {
  let clearCount = 0

  const result = await submitEmailVerification({
    token: 'AB12CD34',
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
  emailVerificationAssert.equal(
    result.feedback.text,
    '인증 요청을 처리하지 못했습니다. 입력한 8자 코드를 유지했으니 잠시 후 다시 시도해 주세요.',
  )
})

emailVerificationTest('successful resend returns the shared privacy-preserving feedback', async () => {
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

emailVerificationTest('failed resend returns retry feedback', async () => {
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
