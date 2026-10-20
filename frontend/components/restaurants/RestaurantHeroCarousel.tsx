'use client'

import { useEffect, useState } from 'react'

import {
  DEFAULT_RESTAURANT_HERO_IMAGE,
  getRestaurantHeroSlideSource,
  resolveRestaurantHeroImageError,
  type RestaurantHeroSlide,
} from '@/lib/restaurant-hero'

import styles from './RestaurantHeroCarousel.module.css'

const AUTOPLAY_INTERVAL_MS = 6_000

type RestaurantHeroCarouselProps = {
  slides: RestaurantHeroSlide[]
}

export function RestaurantHeroCarousel({
  slides,
}: RestaurantHeroCarouselProps) {
  const [activeIndex, setActiveIndex] = useState(0)
  const [isPaused, setIsPaused] = useState(false)
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false)
  const [fallbackSources, setFallbackSources] = useState<Record<string, string>>({})

  useEffect(() => {
    setActiveIndex(0)
    setFallbackSources({})
  }, [slides])

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
    const syncMotionPreference = () => setPrefersReducedMotion(mediaQuery.matches)

    syncMotionPreference()
    mediaQuery.addEventListener?.('change', syncMotionPreference)

    return () => mediaQuery.removeEventListener?.('change', syncMotionPreference)
  }, [])

  useEffect(() => {
    if (slides.length < 2 || isPaused || prefersReducedMotion) {
      return
    }

    const intervalId = window.setInterval(() => {
      setActiveIndex((currentIndex) => (currentIndex + 1) % slides.length)
    }, AUTOPLAY_INTERVAL_MS)

    return () => window.clearInterval(intervalId)
  }, [isPaused, prefersReducedMotion, slides.length])

  if (slides.length === 0) {
    return (
      <div className={styles.emptyState}>
        <img
          src={DEFAULT_RESTAURANT_HERO_IMAGE}
          alt=""
          className={styles.slideImage}
          decoding="async"
        />
      </div>
    )
  }

  return (
    <div
      className={styles.carousel}
      role="region"
      aria-roledescription="carousel"
      aria-label="맛집 추천 이미지"
    >
      <div className={styles.slides}>
        {slides.map((slide, index) => {
          const isActive = index === activeIndex
          const currentSource = getRestaurantHeroSlideSource(
            slide,
            fallbackSources,
          )

          return (
            <div
              key={slide.id}
              className={`${styles.slide} ${isActive ? styles.activeSlide : ''}`}
              role="group"
              aria-roledescription="slide"
              aria-label={`${index + 1} / ${slides.length}: ${slide.name}`}
              aria-hidden={!isActive}
            >
              <img
                src={currentSource}
                alt={`${slide.name} 대표 이미지`}
                className={styles.slideImage}
                loading={index === 0 ? 'eager' : 'lazy'}
                decoding="async"
                onError={() => {
                  const nextSource = resolveRestaurantHeroImageError(
                    currentSource,
                    slide.fallbackSrc,
                  )

                  if (currentSource !== nextSource) {
                    setFallbackSources((current) => ({
                      ...current,
                      [slide.id]: nextSource,
                    }))
                  }
                }}
              />
            </div>
          )
        })}
      </div>

      {slides.length > 1 && !prefersReducedMotion ? (
        <button
          type="button"
          className={styles.pauseButton}
          aria-pressed={isPaused}
          aria-label={
            isPaused
              ? '맛집 이미지 자동 전환 재생'
              : '맛집 이미지 자동 전환 일시 정지'
          }
          onClick={() => setIsPaused((current) => !current)}
        >
          {isPaused ? '재생' : '일시 정지'}
        </button>
      ) : null}
    </div>
  )
}
