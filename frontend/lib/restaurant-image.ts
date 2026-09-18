import { getRestaurantPlaceholderImage } from './restaurant-placeholder-image.ts'

export type RestaurantImageSourceOptions = {
  restaurantId: string
  category: string
  representativeImageUrl?: string | null
}

export type RestaurantImageSource = {
  src: string
  fallbackSrc: string
}

export function resolveRestaurantImageError(
  currentSrc: string,
  fallbackSrc: string,
): string {
  return currentSrc === fallbackSrc ? currentSrc : fallbackSrc
}

function isSafeHttpImageUrl(value: string): boolean {
  try {
    const protocol = new URL(value).protocol
    return protocol === 'http:' || protocol === 'https:'
  } catch {
    return false
  }
}

/**
 * 저장된 YouTube 썸네일을 우선 사용하고, 영상이 없거나 URL이 안전하지 않으면
 * 맛집별로 고정된 카테고리 placeholder를 사용한다.
 */
export function getRestaurantImageSource({
  restaurantId,
  category,
  representativeImageUrl,
}: RestaurantImageSourceOptions): RestaurantImageSource {
  const fallbackSrc = getRestaurantPlaceholderImage(restaurantId, category).src
  const candidate = representativeImageUrl?.trim()

  return {
    src: candidate && isSafeHttpImageUrl(candidate) ? candidate : fallbackSrc,
    fallbackSrc,
  }
}
