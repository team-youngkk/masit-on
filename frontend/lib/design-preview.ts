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
  category: string
  creatorId?: string | null
}

export function shouldUseRestaurantDesignPreview({
  nodeEnv,
  previewFlag,
  hasItems,
  query,
  district,
  category,
  creatorId,
}: RestaurantDesignPreviewInput): boolean {
  return (
    isDesignPreviewEnvironment({ nodeEnv, previewFlag }) &&
    !hasItems &&
    !query.trim() &&
    !district &&
    !category &&
    !creatorId
  )
}
