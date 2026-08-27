export type AnalyticsPath =
  | '/'
  | '/restaurants'
  | '/restaurants/[id]'
  | '/popular'
  | '/creators'
  | '/creators/[id]'
  | '/curations'
  | '/curations/[curationId]'
  | '/course'
  | '/map'
  | '/login'
  | '/signup'
  | '/verify-email'
  | '/password-reset'
  | '/me'
  | '/me/[page]'
  | '/me/collections/[id]'
  | '/admin'
  | '/admin/[page]'
  | '/admin/ai/[jobId]'
  | '/other'

const GA4_MEASUREMENT_ID_PATTERN = /^G-[A-Za-z0-9]+$/

const STATIC_PATHS = new Set<AnalyticsPath>([
  '/',
  '/restaurants',
  '/popular',
  '/creators',
  '/curations',
  '/course',
  '/map',
  '/login',
  '/signup',
  '/verify-email',
  '/password-reset',
  '/me',
  '/admin',
])

export function isValidGa4MeasurementId(
  value: string | undefined,
): value is string {
  return value != null && GA4_MEASUREMENT_ID_PATTERN.test(value.trim())
}

/*
 * GA에는 검색어·쿼리스트링·외부 식별자를 보내지 않는다. 동적 경로는
 * 라우트 템플릿으로 치환해 페이지 유형만 측정한다.
 */
export function toAnalyticsPathname(pathname: string): AnalyticsPath {
  const pathOnly = pathname.split(/[?#]/, 1)[0] || '/'

  if (STATIC_PATHS.has(pathOnly as AnalyticsPath)) {
    return pathOnly as AnalyticsPath
  }

  if (/^\/restaurants\/[^/]+(?:\/.*)?$/.test(pathOnly)) {
    return '/restaurants/[id]'
  }

  if (/^\/creators\/[^/]+(?:\/.*)?$/.test(pathOnly)) {
    return '/creators/[id]'
  }

  if (/^\/curations\/[^/]+(?:\/.*)?$/.test(pathOnly)) {
    return '/curations/[curationId]'
  }

  if (/^\/me\/collections\/[^/]+(?:\/.*)?$/.test(pathOnly)) {
    return '/me/collections/[id]'
  }

  if (pathOnly === '/me/collections' || pathOnly.startsWith('/me/')) {
    return '/me/[page]'
  }

  if (/^\/admin\/ai\/[^/]+(?:\/.*)?$/.test(pathOnly)) {
    return '/admin/ai/[jobId]'
  }

  if (pathOnly.startsWith('/admin/')) {
    return '/admin/[page]'
  }

  return '/other'
}

export type PageViewParams = {
  page_path: AnalyticsPath
  page_location: string
  page_title: '맛잇온'
  page_referrer: ''
}

export function buildPageViewParams(
  pathname: string,
  origin: string,
): PageViewParams {
  const pagePath = toAnalyticsPathname(pathname)
  return {
    page_path: pagePath,
    page_location: `${origin.replace(/\/$/, '')}${pagePath}`,
    page_title: '맛잇온',
    page_referrer: '',
  }
}

declare global {
  interface Window {
    dataLayer?: unknown[]
    gtag?: (...args: unknown[]) => void
  }
}

export function trackPageView(pathname: string): boolean {
  if (typeof window === 'undefined') {
    return false
  }

  const params = buildPageViewParams(pathname, window.location.origin)
  const gtag = window.gtag
  if (typeof gtag === 'function') {
    gtag('event', 'page_view', params)
    return true
  }

  /* 스크립트가 아직 실행되지 않았으면 dataLayer에 안전한 이벤트를 큐잉한다. */
  if (Array.isArray(window.dataLayer)) {
    window.dataLayer.push(['event', 'page_view', params])
    return true
  }

  return false
}
