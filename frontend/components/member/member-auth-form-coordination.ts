export type MemberAuthMode = 'login' | 'signup' | 'request-reset' | 'confirm-reset'

export const PENDING_MEMBER_REGISTRATION_EMAIL_KEY = 'masiton.pending-member-registration-email'
export const PENDING_MEMBER_REGISTRATION_REQUESTED_AT_KEY = 'masiton.pending-member-registration-requested-at'
export const MEMBER_EMAIL_VERIFICATION_TTL_SECONDS = 5 * 60
export const PASSWORD_POLICY_ERROR_MESSAGE = '비밀번호는 12자 이상 64자 이하로 입력해 주세요.'

export type MemberAuthFieldErrors = Readonly<{
  email?: string
  password?: string
  passwordConfirmation?: string
  token?: string
}>

type MemberAuthFormValues = Readonly<{
  mode: MemberAuthMode
  email: string
  password: string
  passwordConfirmation: string
  token: string
}>

const EMAIL_PATTERN = /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$/

export function normalizeMemberEmail(email: string): string {
  return email.trim().toLowerCase()
}

export function extractPasswordResetToken(hash: string): string {
  const fragment = hash.startsWith('#') ? hash.slice(1) : hash
  return new URLSearchParams(fragment).get('token')?.trim() ?? ''
}

export function watchPasswordResetToken(
  readHash: () => string,
  onToken: (token: string) => void,
  clearUrl: () => void,
  subscribe: (listener: () => void) => () => void,
): () => void {
  const applyToken = () => {
    const token = extractPasswordResetToken(readHash())
    if (!token) {
      return
    }
    onToken(token)
    clearUrl()
  }

  applyToken()
  return subscribe(applyToken)
}

export function validateMemberAuthForm(values: MemberAuthFormValues): MemberAuthFieldErrors {
  const errors: {
    email?: string
    password?: string
    passwordConfirmation?: string
    token?: string
  } = {}
  const needsEmail = values.mode !== 'confirm-reset'
  const needsPassword = values.mode === 'login' || values.mode === 'signup' || values.mode === 'confirm-reset'
  const needsPasswordPolicy = values.mode === 'signup' || values.mode === 'confirm-reset'
  const normalizedEmail = normalizeMemberEmail(values.email)

  if (needsEmail) {
    if (!normalizedEmail) {
      errors.email = '이메일을 입력해 주세요.'
    } else if (normalizedEmail.length > 320 || !EMAIL_PATTERN.test(normalizedEmail)) {
      errors.email = '올바른 이메일 형식으로 입력해 주세요.'
    }
  }

  if (values.mode === 'confirm-reset' && !values.token.trim()) {
    errors.token = '비밀번호 재설정 토큰을 입력해 주세요.'
  }

  if (needsPassword && !values.password) {
    errors.password = '비밀번호를 입력해 주세요.'
  } else if (needsPasswordPolicy && (values.password.length < 12 || values.password.length > 64)) {
    errors.password = PASSWORD_POLICY_ERROR_MESSAGE
  } else if (values.mode === 'signup' && values.password === normalizedEmail) {
    errors.password = '비밀번호는 이메일과 다르게 입력해 주세요.'
  }

  if (needsPasswordPolicy) {
    if (!values.passwordConfirmation) {
      errors.passwordConfirmation = '비밀번호 확인을 입력해 주세요.'
    } else if (values.password !== values.passwordConfirmation) {
      errors.passwordConfirmation = '비밀번호가 일치하지 않습니다.'
    }
  }

  return errors
}

export function prepareMemberAuthSubmission(values: MemberAuthFormValues): Readonly<{
  errors: MemberAuthFieldErrors
  normalizedEmail: string
}> {
  return {
    errors: validateMemberAuthForm(values),
    normalizedEmail: normalizeMemberEmail(values.email),
  }
}

export function isInvalidMemberCredentialsResponse(reason: unknown): boolean {
  return reason instanceof Response && reason.status === 401
}
