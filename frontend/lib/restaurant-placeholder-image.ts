const PLACEHOLDER_BASE_PATH =
  '/images/restaurant-placeholders/food-scenes-final'
const PLACEHOLDER_VARIANT_COUNT = 3

export type RestaurantPlaceholderImage = {
  src: string
  categorySlug: string
  variant: number
}

const CATEGORY_SLUGS_BY_NAME: Record<string, string> = {
  한식: 'korean-food',
  중식: 'chinese-food',
  일식: 'japanese-food',
  양식: 'western-food',
  '동남아 음식': 'seafood',
  '인도·남아시아 음식': 'korean-food',
  분식: 'bunsik',
  '카페·디저트': 'cafe-drink',
  '술집·주점': 'japanese-food',
  기타: 'korean-food',
}

const CATEGORY_SLUGS_BY_KEYWORD: Array<{ keywords: string[]; slug: string }> = [
  { keywords: ['카페', '커피', '음료', '차'], slug: 'cafe-drink' },
  { keywords: ['디저트', '케이크', '베이커리', '빵'], slug: 'dessert' },
  { keywords: ['면', '라멘', '냉면', '국수', '우동', '파스타'], slug: 'noodles' },
  { keywords: ['고기', '곱창', '구이', '갈비', '육류'], slug: 'meat' },
  { keywords: ['해산물', '회', '조개', '생선', '새우', '수산'], slug: 'seafood' },
  { keywords: ['분식', '떡볶이', '김밥', '순대', '튀김'], slug: 'bunsik' },
  { keywords: ['중식', '짜장', '짬뽕', '딤섬'], slug: 'chinese-food' },
  { keywords: ['일식', '이자카야', '초밥', '스시', '돈카츠'], slug: 'japanese-food' },
  { keywords: ['양식', '스테이크', '피자'], slug: 'western-food' },
  { keywords: ['한식', '국밥', '찌개', '비빔밥'], slug: 'korean-food' },
]

function stableHash(value: string): number {
  let hash = 2166136261

  for (const character of value) {
    hash ^= character.codePointAt(0) ?? 0
    hash = Math.imul(hash, 16777619)
  }

  return hash >>> 0
}

function resolveCategorySlug(category: string): string {
  const normalizedCategory = category.trim().toLowerCase()
  const exactCategorySlug = Object.entries(CATEGORY_SLUGS_BY_NAME).find(
    ([categoryName]) => categoryName.toLowerCase() === normalizedCategory,
  )?.[1]
  if (exactCategorySlug) {
    return exactCategorySlug
  }

  const matchedCategory = CATEGORY_SLUGS_BY_KEYWORD.find(({ keywords }) =>
    keywords.some((keyword) => normalizedCategory.includes(keyword)),
  )

  return matchedCategory?.slug ?? 'korean-food'
}

export function getRestaurantPlaceholderImage(
  restaurantId: string,
  category: string,
): RestaurantPlaceholderImage {
  const categorySlug = resolveCategorySlug(category)
  const variant = (stableHash(`${categorySlug}:${restaurantId}`) % PLACEHOLDER_VARIANT_COUNT) + 1

  return {
    categorySlug,
    variant,
    src: `${PLACEHOLDER_BASE_PATH}/${categorySlug}/${String(variant).padStart(2, '0')}.webp`,
  }
}
