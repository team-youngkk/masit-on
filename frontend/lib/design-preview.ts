type DesignPreviewEnvironment = {
  nodeEnv?: string
  previewFlag?: string
}

export function isDesignPreviewEnvironment({
  nodeEnv,
  previewFlag,
}: DesignPreviewEnvironment): boolean {
  return nodeEnv !== 'production' && previewFlag === '1'
}

type RestaurantDesignPreviewInput = DesignPreviewEnvironment & {
  hasItems: boolean
  query: string
  district: string
  regionCode?: string
  tag?: string
  category: string
  creatorId?: string | null
}

export function shouldUseRestaurantDesignPreview({
  nodeEnv,
  previewFlag,
  hasItems,
  query,
  district,
  regionCode,
  tag,
  category,
  creatorId,
}: RestaurantDesignPreviewInput): boolean {
  return (
    isDesignPreviewEnvironment({ nodeEnv, previewFlag }) &&
    !hasItems &&
    !query.trim() &&
    !district &&
    !regionCode &&
    !tag &&
    !category &&
    !creatorId
  )
}
