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

const ADMIN_RETURN_TO_PATHS = new Set([
  '/admin',
  '/admin/restaurants/new',
  '/admin/creators/new',
  '/admin/videos/new',
  '/admin/visits/new',
])

const AUTHENTICATION_PATHS = new Set(['/login', '/signup', '/verify-email', '/password-reset', '/admin/login'])

export function safeAdminReturnTo(returnTo: string | null | undefined): string | null {
  if (!returnTo || returnTo.includes('\\') || /%25/i.test(returnTo)) return null
  const safeReturnTo = safeMemberReturnTo(returnTo)
  if (!safeReturnTo) return null
  try {
    const parsed = new URL(safeReturnTo, 'https://masiton.local')
    return !parsed.search && !parsed.hash && ADMIN_RETURN_TO_PATHS.has(parsed.pathname)
      ? parsed.pathname
      : null
  } catch { return null }
}

export function memberLoginDestination(returnTo: string | null | undefined): string {
  if (returnTo && (returnTo.includes('\\') || /%25/i.test(returnTo))) return '/restaurants'

  const safeAdminPath = safeAdminReturnTo(returnTo)
  if (safeAdminPath) return safeAdminPath

  const safeMemberPath = safeMemberReturnTo(returnTo)
  if (!safeMemberPath) return '/restaurants'

  const pathname = new URL(safeMemberPath, 'https://masiton.local').pathname
  return AUTHENTICATION_PATHS.has(pathname) ? '/restaurants' : safeMemberPath
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
