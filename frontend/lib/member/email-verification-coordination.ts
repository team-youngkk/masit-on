export type EmailVerificationFeedback = {
  kind: 'status' | 'alert'
  text: string
}

type SingleFlightAction<T> = () => Promise<T>

type SubmitEmailVerificationOptions = {
  token: string
  verify: (token: string) => Promise<void>
  clearToken: () => void
}

type ResendEmailVerificationOptions = {
  email: string
  resend: (email: string) => Promise<void>
}

export type SubmitEmailVerificationResult = {
  verified: boolean
  shouldOfferResend: boolean
  feedback: EmailVerificationFeedback
}

export type ResendEmailVerificationResult = {
  feedback: EmailVerificationFeedback
}

const VERIFICATION_SUCCESS_MESSAGE =
  '이메일 인증이 완료되었습니다. 자동 로그인되지 않으므로 로그인 화면에서 다시 로그인해 주세요.'
const VERIFICATION_FAILURE_MESSAGE =
  '이메일 인증을 완료하지 못했습니다. 토큰을 다시 확인하거나 아래에서 인증 메일을 다시 요청해 주세요.'
const VERIFICATION_RETRY_MESSAGE =
  '이메일 인증 요청을 처리하지 못했습니다. 입력한 토큰을 유지했으니 잠시 후 다시 시도해 주세요.'
const RESEND_SUCCESS_MESSAGE =
  '인증 메일 재발송 요청을 접수했습니다. 계정 상태나 실제 발송 여부와 관계없이 같은 안내를 제공합니다.'
const RESEND_FAILURE_MESSAGE =
  '인증 메일 재발송 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'

export function createEmailVerificationSingleFlight(): {
  run<T>(action: SingleFlightAction<T>): Promise<T>
} {
  let inFlight: Promise<unknown> | null = null

  return {
    run<T>(action: SingleFlightAction<T>): Promise<T> {
      if (inFlight) {
        return inFlight as Promise<T>
      }

      let running: Promise<T>
      try {
        running = Promise.resolve(action())
      } catch (error) {
        running = Promise.reject(error)
      }
      const guarded = running.finally(() => {
        if (inFlight === guarded) {
          inFlight = null
        }
      })

      inFlight = guarded
      return guarded as Promise<T>
    },
  }
}

export async function submitEmailVerification({
  token,
  verify,
  clearToken,
}: SubmitEmailVerificationOptions): Promise<SubmitEmailVerificationResult> {
  try {
    await verify(token)
    clearToken()
    return {
      verified: true,
      shouldOfferResend: false,
      feedback: {
        kind: 'status',
        text: VERIFICATION_SUCCESS_MESSAGE,
      },
    }
  } catch (error) {
    if (!(error instanceof Response) || error.status !== 400) {
      return {
        verified: false,
        shouldOfferResend: false,
        feedback: {
          kind: 'alert',
          text: VERIFICATION_RETRY_MESSAGE,
        },
      }
    }

    clearToken()
    return {
      verified: false,
      shouldOfferResend: true,
      feedback: {
        kind: 'alert',
        text: VERIFICATION_FAILURE_MESSAGE,
      },
    }
  }
}

export async function resendEmailVerification({
  email,
  resend,
}: ResendEmailVerificationOptions): Promise<ResendEmailVerificationResult> {
  try {
    await resend(email)
    return {
      feedback: {
        kind: 'status',
        text: RESEND_SUCCESS_MESSAGE,
      },
    }
  } catch {
    return {
      feedback: {
        kind: 'alert',
        text: RESEND_FAILURE_MESSAGE,
      },
    }
  }
}
