const memberAuthFormAssert = require('node:assert/strict')
const memberAuthFormTest = require('node:test')
const {
  isInvalidMemberCredentialsResponse,
  normalizeMemberEmail,
  prepareMemberAuthSubmission,
  validateMemberAuthForm,
  extractPasswordResetToken,
  watchPasswordResetToken,
} = require('./member-auth-form-coordination.ts')

function validate(overrides: Record<string, string> = {}) {
  return validateMemberAuthForm({
    mode: 'signup',
    email: 'member@example.com',
    password: '123456789012',
    passwordConfirmation: '123456789012',
    token: '',
    ...overrides,
  })
}

memberAuthFormTest('이메일은 앞뒤 공백을 제거하고 소문자로 정규화한다', () => {
  memberAuthFormAssert.equal(normalizeMemberEmail(' Member@Example.COM '), 'member@example.com')
})

memberAuthFormTest('비밀번호 재설정 fragment에서 토큰만 읽고 잘못된 fragment는 비운다', () => {
  memberAuthFormAssert.equal(extractPasswordResetToken('#token=%20opaque-reset-token%20'), 'opaque-reset-token')
  memberAuthFormAssert.equal(extractPasswordResetToken('#next=1'), '')
  memberAuthFormAssert.equal(extractPasswordResetToken(''), '')
})

memberAuthFormTest('비밀번호 재설정 화면은 처음과 hash 변경 때 최신 토큰을 읽고 주소를 지운다', () => {
  let hash = '#token=first-reset-token'
  let listener: (() => void) | undefined
  const tokens: string[] = []
  let clearCount = 0

  const unsubscribe = watchPasswordResetToken(
    () => hash,
    (token: string) => tokens.push(token),
    () => { clearCount += 1 },
    (nextListener: () => void) => {
      listener = nextListener
      return () => { listener = undefined }
    },
  )

  hash = '#token=second-reset-token'
  listener?.()

  memberAuthFormAssert.deepEqual(tokens, ['first-reset-token', 'second-reset-token'])
  memberAuthFormAssert.equal(clearCount, 2)

  unsubscribe()
  hash = '#token=third-reset-token'
  listener?.()
  memberAuthFormAssert.deepEqual(tokens, ['first-reset-token', 'second-reset-token'])
})

memberAuthFormTest('이메일을 사용하는 제출 모드는 API 요청용 정규화 이메일을 만든다', () => {
  for (const mode of ['login', 'signup', 'request-reset']) {
    const result = prepareMemberAuthSubmission({
      mode,
      email: ' Registered@Example.COM ',
      password: mode === 'request-reset' ? '' : '123456789012',
      passwordConfirmation: mode === 'signup' ? '123456789012' : '',
      token: '',
    })

    memberAuthFormAssert.deepEqual(result.errors, {})
    memberAuthFormAssert.equal(result.normalizedEmail, 'registered@example.com')
  }
})

memberAuthFormTest('필수 이메일과 이메일 형식을 요청 전에 검증한다', () => {
  memberAuthFormAssert.deepEqual(validate({ email: '   ' }), {
    email: '이메일을 입력해 주세요.',
  })
  memberAuthFormAssert.deepEqual(validate({ email: 'member@' }), {
    email: '올바른 이메일 형식으로 입력해 주세요.',
  })
})

memberAuthFormTest('이메일이 필요한 각 모드는 빈 이메일을 거부한다', () => {
  for (const mode of ['login', 'signup', 'request-reset']) {
    memberAuthFormAssert.equal(validateMemberAuthForm({
      mode,
      email: '',
      password: '123456789012',
      passwordConfirmation: '123456789012',
      token: '',
    }).email, '이메일을 입력해 주세요.')
  }
})

memberAuthFormTest('로그인은 빈 비밀번호를 요청 전에 거부한다', () => {
  memberAuthFormAssert.deepEqual(validateMemberAuthForm({
    mode: 'login',
    email: 'member@example.com',
    password: '',
    passwordConfirmation: '',
    token: '',
  }), {
    password: '비밀번호를 입력해 주세요.',
  })
})

memberAuthFormTest('가입 비밀번호는 12자와 64자 경계를 허용한다', () => {
  memberAuthFormAssert.deepEqual(validate({ password: 'a'.repeat(12), passwordConfirmation: 'a'.repeat(12) }), {})
  memberAuthFormAssert.deepEqual(validate({ password: 'a'.repeat(64), passwordConfirmation: 'a'.repeat(64) }), {})
  memberAuthFormAssert.equal(validate({ password: 'a'.repeat(11), passwordConfirmation: 'a'.repeat(11) }).password, '비밀번호는 12자 이상 64자 이하로 입력해 주세요.')
  memberAuthFormAssert.equal(validate({ password: 'a'.repeat(65), passwordConfirmation: 'a'.repeat(65) }).password, '비밀번호는 12자 이상 64자 이하로 입력해 주세요.')
})

memberAuthFormTest('가입 비밀번호는 정규화 이메일과 같을 수 없고 확인값이 일치해야 한다', () => {
  memberAuthFormAssert.equal(validate({ email: ' Member@Example.COM ', password: 'member@example.com', passwordConfirmation: 'member@example.com' }).password, '비밀번호는 이메일과 다르게 입력해 주세요.')
  memberAuthFormAssert.equal(validate({ passwordConfirmation: 'different-password' }).passwordConfirmation, '비밀번호가 일치하지 않습니다.')
})

memberAuthFormTest('비밀번호 변경은 토큰과 확인값 및 12~64자 정책을 검증한다', () => {
  const errors = validateMemberAuthForm({
    mode: 'confirm-reset',
    email: '',
    password: 'short',
    passwordConfirmation: '',
    token: '   ',
  })

  memberAuthFormAssert.deepEqual(errors, {
    token: '비밀번호 재설정 토큰을 입력해 주세요.',
    password: '비밀번호는 12자 이상 64자 이하로 입력해 주세요.',
    passwordConfirmation: '비밀번호 확인을 입력해 주세요.',
  })
})

memberAuthFormTest('로그인은 계정 상태 추론을 막기 위해 비밀번호 정책을 사전 판정하지 않는다', () => {
  memberAuthFormAssert.deepEqual(validateMemberAuthForm({
    mode: 'login',
    email: 'member@example.com',
    password: 'short',
    passwordConfirmation: '',
    token: '',
  }), {})
})

memberAuthFormTest('로그인 자격 증명 오류는 401 응답만 입력 오류로 구분한다', () => {
  memberAuthFormAssert.equal(isInvalidMemberCredentialsResponse(new Response(null, { status: 401 })), true)
  memberAuthFormAssert.equal(isInvalidMemberCredentialsResponse(new Response(null, { status: 503 })), false)
  memberAuthFormAssert.equal(isInvalidMemberCredentialsResponse(new Error('network')), false)
})
