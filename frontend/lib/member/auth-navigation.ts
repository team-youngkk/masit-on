export function safeMemberReturnTo(returnTo: string | null | undefined): string | null {
  if (!returnTo?.startsWith('/') || returnTo.startsWith('//')) {
    return null
  }

  try {
    const destination = new URL(returnTo, 'https://masiton.local')
    if (destination.origin !== 'https://masiton.local') {
      return null
    }
    const normalizedReturnTo = `${destination.pathname}${destination.search}${destination.hash}`
    return normalizedReturnTo.startsWith('//') ? null : normalizedReturnTo
  } catch {
    return null
  }
}

function memberAuthHref(pathname: '/login' | '/signup' | '/verify-email', returnTo: string | null | undefined): string {
  const safeReturnTo = safeMemberReturnTo(returnTo)
  if (!safeReturnTo) {
    return pathname
  }

  return `${pathname}?${new URLSearchParams({ returnTo: safeReturnTo }).toString()}`
}

export function memberLoginHref(returnTo: string | null | undefined): string {
  return memberAuthHref('/login', returnTo)
}

export function memberSignupHref(returnTo: string | null | undefined): string {
  return memberAuthHref('/signup', returnTo)
}

export function memberVerifyEmailHref(returnTo: string | null | undefined): string {
  return memberAuthHref('/verify-email', returnTo)
}
