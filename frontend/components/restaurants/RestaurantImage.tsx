'use client'

import { useEffect, useState } from 'react'

import {
  getRestaurantImageSource,
  resolveRestaurantImageError,
} from '@/lib/restaurant-image'

type RestaurantImageProps = {
  restaurantId: string
  category: string
  representativeImageUrl?: string | null
  alt: string
  className?: string
  loading?: 'eager' | 'lazy'
}

export function RestaurantImage({
  restaurantId,
  category,
  representativeImageUrl,
  alt,
  className,
  loading = 'lazy',
}: RestaurantImageProps) {
  const source = getRestaurantImageSource({
    restaurantId,
    category,
    representativeImageUrl,
  })
  const [src, setSrc] = useState(source.src)

  useEffect(() => {
    setSrc(source.src)
  }, [source.src])

  return (
    <img
      src={src}
      alt={alt}
      className={className}
      loading={loading}
      decoding="async"
      onError={() =>
        setSrc((currentSrc) =>
          resolveRestaurantImageError(currentSrc, source.fallbackSrc),
        )
      }
    />
  )
}
