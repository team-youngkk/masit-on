import {
  getRestaurantImageSource,
  type RestaurantImageSource,
} from './restaurant-image.ts'

export type RestaurantHeroRestaurant = {
  id: string
  name: string
  category: string
  representativeImageUrl: string | null
}

export type RestaurantHeroSlide = RestaurantImageSource & {
  id: string
  name: string
}

export const DEFAULT_RESTAURANT_HERO_IMAGE = '/images/restaurant-hero-mascot.png'

export function buildRestaurantHeroSlides(
  restaurants: RestaurantHeroRestaurant[],
): RestaurantHeroSlide[] {
  return restaurants.map((restaurant) => {
    const image = getRestaurantImageSource({
      restaurantId: restaurant.id,
      category: restaurant.category,
      representativeImageUrl: restaurant.representativeImageUrl,
    })

    return {
      id: restaurant.id,
      name: restaurant.name,
      ...image,
    }
  })
}

export function resolveRestaurantHeroImageError(
  currentSrc: string,
  fallbackSrc: string,
): string {
  if (currentSrc === DEFAULT_RESTAURANT_HERO_IMAGE) {
    return currentSrc
  }

  return currentSrc === fallbackSrc
    ? DEFAULT_RESTAURANT_HERO_IMAGE
    : fallbackSrc
}

export function getRestaurantHeroSlideSource(
  slide: RestaurantHeroSlide,
  fallbackSources: Readonly<Record<string, string>>,
): string {
  return Object.hasOwn(fallbackSources, slide.id)
    ? fallbackSources[slide.id]
    : slide.src
}

/* 서버 렌더 결과에 순서를 함께 포함해 첫 화면 hydration과 랜덤 시작을 일치시킨다. */
export function shuffleRestaurantHeroSlides(
  slides: RestaurantHeroSlide[],
  random: () => number = Math.random,
): RestaurantHeroSlide[] {
  if (slides.length < 2) {
    return [...slides]
  }

  const shuffled = [...slides]
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const randomIndex = Math.floor(random() * (index + 1))
    ;[shuffled[index], shuffled[randomIndex]] = [
      shuffled[randomIndex],
      shuffled[index],
    ]
  }

  /* 랜덤 결과가 우연히 원래 순서와 같아지는 경우도 피한다. */
  if (shuffled.every((slide, index) => slide.id === slides[index]?.id)) {
    ;[shuffled[0], shuffled[1]] = [shuffled[1], shuffled[0]]
  }

  return shuffled
}
