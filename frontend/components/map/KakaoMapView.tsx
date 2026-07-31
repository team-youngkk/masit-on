'use client'

import { useEffect, useRef, useState } from 'react'

import type { MapBounds } from '@/lib/map/map-points-query'
import type { MapPointItem } from '@/lib/map/map-points-response'

import type { KakaoGlobal, KakaoMap, KakaoMarker, KakaoMarkerImage } from './kakao-maps-types'

import styles from './KakaoMapView.module.css'

/*
 * 브라우저에 노출되는 식별자이며 비밀키가 아니다. 허용 도메인 제한은 Kakao 콘솔에서
 * 적용한다(ADR-MAP-001 6.5). 커밋된 기본값을 두지 않는다.
 */
const KAKAO_MAPS_JS_KEY = process.env.NEXT_PUBLIC_KAKAO_MAPS_JS_KEY
const SDK_LOAD_TIMEOUT_MS = 10_000
const IDLE_DEBOUNCE_MS = 300
const SCRIPT_ELEMENT_ID = 'kakao-maps-sdk'

declare global {
  interface Window {
    kakao?: KakaoGlobal
  }
}

type KakaoMapViewProps = {
  items: MapPointItem[]
  selectedId: string | null
  fallbackBounds: MapBounds
  onSelect: (id: string) => void
  onBoundsChange: (bounds: MapBounds) => void
}

function loadKakaoMapsSdk(key: string, timeoutMs: number): Promise<KakaoGlobal> {
  return new Promise((resolve, reject) => {
    const existing = window.kakao
    if (existing?.maps?.Map) {
      resolve(existing)
      return
    }

    const timer = setTimeout(() => {
      reject(new Error('Kakao Maps SDK load timed out'))
    }, timeoutMs)

    /*
     * 이전 시도가 실패한 script 요소가 남아 있으면 이미 한 번 load/error 이벤트를 소진했으므로
     * 리스너만 새로 붙여도 다시 발화하지 않는다. 재시도가 실제 네트워크 재요청이 되도록
     * 항상 제거하고 새 src로 새 요소를 만든다.
     */
    document.getElementById(SCRIPT_ELEMENT_ID)?.remove()

    const script = document.createElement('script')
    script.id = SCRIPT_ELEMENT_ID
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(key)}&autoload=false`
    script.async = true

    script.addEventListener('load', () => {
      const kakaoGlobal = window.kakao
      if (!kakaoGlobal) {
        clearTimeout(timer)
        reject(new Error('Kakao Maps SDK unavailable after load'))
        return
      }
      kakaoGlobal.maps.load(() => {
        clearTimeout(timer)
        resolve(kakaoGlobal)
      })
    })
    script.addEventListener('error', () => {
      clearTimeout(timer)
      reject(new Error('Kakao Maps SDK failed to load'))
    })

    document.head.appendChild(script)
  })
}

function toMapBounds(map: KakaoMap): MapBounds {
  const bounds = map.getBounds()
  const southWest = bounds.getSouthWest()
  const northEast = bounds.getNorthEast()
  return {
    south: southWest.getLat(),
    west: southWest.getLng(),
    north: northEast.getLat(),
    east: northEast.getLng(),
  }
}

/* 기본 마커와 선택 마커를 구분하는 최소 SVG 핀. 외부 이미지 자산을 추가하지 않는다. */
function createMarkerImage(kakaoGlobal: KakaoGlobal, selected: boolean): KakaoMarkerImage {
  const fill = selected ? '%2316a34a' : '%236f6a63'
  const svg =
    `data:image/svg+xml,` +
    `%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='40' viewBox='0 0 32 40'%3E` +
    `%3Cpath fill='${fill}' d='M16 0C7.163 0 0 7.163 0 16c0 12 16 24 16 24s16-12 16-24C32 7.163 24.837 0 16 0z'/%3E` +
    `%3Ccircle cx='16' cy='16' r='6' fill='white'/%3E` +
    `%3C/svg%3E`
  return new kakaoGlobal.maps.MarkerImage(svg, new kakaoGlobal.maps.Size(32, 40))
}

export function KakaoMapView({
  items,
  selectedId,
  fallbackBounds,
  onSelect,
  onBoundsChange,
}: KakaoMapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<KakaoMap | null>(null)
  const kakaoRef = useRef<KakaoGlobal | null>(null)
  const markersRef = useRef<KakaoMarker[]>([])
  const idleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const onSelectRef = useRef(onSelect)
  const onBoundsChangeRef = useRef(onBoundsChange)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [retryCount, setRetryCount] = useState(0)

  onSelectRef.current = onSelect
  onBoundsChangeRef.current = onBoundsChange

  useEffect(() => {
    if (!KAKAO_MAPS_JS_KEY) {
      setStatus('error')
      return
    }

    let cancelled = false
    setStatus('loading')

    loadKakaoMapsSdk(KAKAO_MAPS_JS_KEY, SDK_LOAD_TIMEOUT_MS)
      .then((kakaoGlobal) => {
        if (cancelled || !containerRef.current) {
          return
        }

        kakaoRef.current = kakaoGlobal
        const center = new kakaoGlobal.maps.LatLng(
          (fallbackBounds.south + fallbackBounds.north) / 2,
          (fallbackBounds.west + fallbackBounds.east) / 2,
        )
        const map = new kakaoGlobal.maps.Map(containerRef.current, {
          center,
          level: 8,
        })
        mapRef.current = map

        const handleIdle = () => {
          if (idleTimerRef.current) {
            clearTimeout(idleTimerRef.current)
          }
          idleTimerRef.current = setTimeout(() => {
            onBoundsChangeRef.current(toMapBounds(map))
          }, IDLE_DEBOUNCE_MS)
        }

        kakaoGlobal.maps.event.addListener(map, 'idle', handleIdle)
        setStatus('ready')
        handleIdle()
      })
      .catch(() => {
        if (!cancelled) {
          setStatus('error')
        }
      })

    return () => {
      cancelled = true
      if (idleTimerRef.current) {
        clearTimeout(idleTimerRef.current)
      }
    }
    // fallbackBounds는 최초 중심 좌표 계산에만 쓰고 이후 변경에는 반응하지 않는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [retryCount])

  useEffect(() => {
    const kakaoGlobal = kakaoRef.current
    const map = mapRef.current
    if (!kakaoGlobal || !map || status !== 'ready') {
      return
    }

    for (const marker of markersRef.current) {
      marker.setMap(null)
    }

    const selectedImage = createMarkerImage(kakaoGlobal, true)
    const defaultImage = createMarkerImage(kakaoGlobal, false)

    markersRef.current = items.map((item) => {
      const position = new kakaoGlobal.maps.LatLng(
        item.coordinate.latitude,
        item.coordinate.longitude,
      )
      const marker = new kakaoGlobal.maps.Marker({
        position,
        map,
        image: item.id === selectedId ? selectedImage : defaultImage,
      })
      kakaoGlobal.maps.event.addListener(marker, 'click', () => {
        onSelectRef.current(item.id)
      })
      return marker
    })
  }, [items, selectedId, status])

  useEffect(() => {
    const kakaoGlobal = kakaoRef.current
    const map = mapRef.current
    if (!kakaoGlobal || !map || status !== 'ready' || !selectedId) {
      return
    }

    const selectedItem = items.find((item) => item.id === selectedId)
    if (!selectedItem) {
      return
    }

    map.panTo(
      new kakaoGlobal.maps.LatLng(
        selectedItem.coordinate.latitude,
        selectedItem.coordinate.longitude,
      ),
    )
  }, [items, selectedId, status])

  return (
    <div className={styles.container}>
      <div ref={containerRef} className={styles.map} role="group" aria-label="지도" />
      {status === 'loading' ? (
        <p className={styles.overlay} aria-live="polite">
          지도를 불러오는 중입니다.
        </p>
      ) : null}
      {status === 'error' ? (
        <div className={styles.overlay} role="alert">
          <p>지도를 불러올 수 없습니다.</p>
          <button
            type="button"
            className={styles.retry}
            onClick={() => setRetryCount((count) => count + 1)}
          >
            다시 시도
          </button>
        </div>
      ) : null}
    </div>
  )
}
