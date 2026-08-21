export type RestaurantStructuredFilterKey =
  | 'query'
  | 'district'
  | 'category'
  | 'creatorId'

const STRUCTURED_FILTER_KEYS: readonly RestaurantStructuredFilterKey[] = [
  'query',
  'district',
  'category',
  'creatorId',
]
const DEFAULT_SIZE = '21'
const ALLOWED_SIZES = new Set(['10', '20', '21', '50'])

function buildRestaurantFilterHref(
  current: Pick<URLSearchParams, 'get'>,
  excludedKeys: ReadonlySet<RestaurantStructuredFilterKey>,
): string {
  const next = new URLSearchParams()

  for (const key of STRUCTURED_FILTER_KEYS) {
    const rawValue = current.get(key)
    const value = key === 'creatorId' ? rawValue : rawValue?.trim()
    if (!excludedKeys.has(key) && value) {
      next.set(key, value)
    }
  }

  next.set('page', '1')
  const requestedSize = current.get('size')?.trim()
  next.set(
    'size',
    requestedSize && ALLOWED_SIZES.has(requestedSize)
      ? requestedSize
      : DEFAULT_SIZE,
  )
  return `/restaurants?${next.toString()}`
}

export function buildRestaurantFilterClearHref(
  current: Pick<URLSearchParams, 'get'>,
  key: RestaurantStructuredFilterKey,
): string {
  return buildRestaurantFilterHref(current, new Set([key]))
}

export function buildRestaurantFiltersResetHref(
  current: Pick<URLSearchParams, 'get'>,
): string {
  return buildRestaurantFilterHref(current, new Set(STRUCTURED_FILTER_KEYS))
}
