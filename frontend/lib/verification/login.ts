export const DEFAULT_VERIFICATION_RETURN_TO = '/'

export function safeVerificationReturnTo(returnTo: string | null | undefined): string {
  if (!returnTo?.startsWith('/') || returnTo.startsWith('//')) {
    return DEFAULT_VERIFICATION_RETURN_TO
  }

  try {
    const destination = new URL(returnTo, 'https://masiton.local')
    if (destination.origin !== 'https://masiton.local') {
      return DEFAULT_VERIFICATION_RETURN_TO
    }

    const normalized = `${destination.pathname}${destination.search}${destination.hash}`
    return normalized.startsWith('//') ? DEFAULT_VERIFICATION_RETURN_TO : normalized
  } catch {
    return DEFAULT_VERIFICATION_RETURN_TO
  }
}

export function verificationReturnToFromHash(hash: string): string | null {
  const prefix = '#returnTo='
  return hash.startsWith(prefix) ? hash.slice(prefix.length) : null
}

export type VerificationLoginResult =
  | { ok: true }
  | { ok: false; message: string }

export function verificationLoginResult(status: number): VerificationLoginResult {
  if (status === 204) {
    return { ok: true }
  }

  if (status === 401) {
    return {
      ok: false,
      message: '로그인 정보를 확인할 수 없습니다. 입력한 정보를 다시 확인해 주세요.',
    }
  }

  if (status === 429) {
    return {
      ok: false,
      message: '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.',
    }
  }

  if (status === 503) {
    return {
      ok: false,
      message: '현재 검증 참여자 로그인을 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.',
    }
  }

  return {
    ok: false,
    message: '로그인 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.',
  }
}
