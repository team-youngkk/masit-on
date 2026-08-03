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

export function memberSignupHref(returnTo: string | null | undefined): string {
  const safeReturnTo = safeMemberReturnTo(returnTo)
  if (!safeReturnTo) {
    return '/signup'
  }

  return `/signup?${new URLSearchParams({ returnTo: safeReturnTo }).toString()}`
}
