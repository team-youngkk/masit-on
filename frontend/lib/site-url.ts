const HTTPS_PROTOCOL = 'https:'

/**
 * Public SEO URLs must have an explicitly configured HTTPS origin. Returning
 * null keeps callers from accidentally publishing localhost or request-derived
 * URLs when the deployment configuration is incomplete.
 */
export function parseSiteUrl(value: string | undefined): URL | null {
  const trimmedValue = value?.trim()
  if (!trimmedValue) {
    return null
  }

  try {
    const url = new URL(trimmedValue)
    if (
      url.protocol !== HTTPS_PROTOCOL ||
      !url.hostname ||
      url.pathname !== '/' ||
      url.username ||
      url.password ||
      url.search ||
      url.hash
    ) {
      return null
    }
    return url
  } catch {
    return null
  }
}

export function getSiteUrl(): URL | null {
  return parseSiteUrl(process.env['NEXT_PUBLIC_SITE_URL'])
}

export function toPublicSiteUrl(pathname: string): string | null {
  if (!pathname.startsWith('/') || pathname.startsWith('//')) {
    return null
  }

  const siteUrl = getSiteUrl()
  return siteUrl ? new URL(pathname, siteUrl).toString() : null
}
