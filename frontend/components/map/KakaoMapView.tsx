'use client'

import { useEffect, useRef, useState } from 'react'

import type { MapBounds } from '@/lib/map/map-points-query'
import type { MapPointItem } from '@/lib/map/map-points-response'

import type { KakaoGlobal, KakaoMap, KakaoMarker, KakaoMarkerImage } from './kakao-maps-types'

import styles from './KakaoMapView.module.css'

/*
 * 브라우저에 노출되는 식별자이며 비밀키가 아니다. 허용 도메인 제한은 Kakao 콘솔에서
 * 적용한다(ADR-MAP-001 4.5). 커밋된 기본값을 두지 않는다.
 */
const KAKAO_MAPS_JS_KEY = process.env.NEXT_PUBLIC_KAKAO_MAPS_JS_KEY
const SDK_LOAD_TIMEOUT_MS = 10_000
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
}

/*
 * `/course` 결과 지도(CourseRouteMap)와 공유하는 유일한 경계다(D-231-05,
 * docs/08-planning/issue-231-course-route-map.md). 이 함수 밖의 상태·마커 로직은
 * 공유하지 않는다.
 */
export function loadKakaoMapsSdk(key: string, timeoutMs: number): Promise<KakaoGlobal> {
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
}: KakaoMapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<KakaoMap | null>(null)
  const kakaoRef = useRef<KakaoGlobal | null>(null)
  const markersRef = useRef<KakaoMarker[]>([])
  const markersByIdRef = useRef<Map<string, KakaoMarker>>(new Map())
  const previousSelectedIdRef = useRef<string | null>(null)
  const onSelectRef = useRef(onSelect)
  const selectedIdRef = useRef(selectedId)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [retryCount, setRetryCount] = useState(0)

  onSelectRef.current = onSelect
  selectedIdRef.current = selectedId

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

        setStatus('ready')
      })
      .catch(() => {
        if (!cancelled) {
          setStatus('error')
        }
      })

    return () => {
      cancelled = true
    }
    // fallbackBounds는 최초 중심 좌표 계산에만 쓰고 이후 변경에는 반응하지 않는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [retryCount])

  /*
   * items가 바뀔 때만 마커 집합을 처음부터 다시 만든다. selectedId를 의존성에 넣지 않아
   * 마커 선택/해제만으로는(최대 200개까지 있을 수 있는) 전체 마커를 지우고 다시 만들지
   * 않는다(Finding C). 마커는 항상 기본 이미지로 만들고, 현재 selectedId에 해당하는
   * 마커가 있으면 즉시 선택 이미지로 되돌려 items 갱신 전후로 선택 표시가 유지되게 한다.
   */
  useEffect(() => {
    const kakaoGlobal = kakaoRef.current
    const map = mapRef.current
    if (!kakaoGlobal || !map || status !== 'ready') {
      return
    }

    for (const marker of markersRef.current) {
      marker.setMap(null)
    }

    const defaultImage = createMarkerImage(kakaoGlobal, false)
    const markersById = new Map<string, KakaoMarker>()

    markersRef.current = items.map((item) => {
      const position = new kakaoGlobal.maps.LatLng(
        item.coordinate.latitude,
        item.coordinate.longitude,
      )
      const marker = new kakaoGlobal.maps.Marker({
        position,
        map,
        image: defaultImage,
      })
      kakaoGlobal.maps.event.addListener(marker, 'click', () => {
        onSelectRef.current(item.id)
      })
      markersById.set(item.id, marker)
      return marker
    })

    markersByIdRef.current = markersById

    const currentSelectedId = selectedIdRef.current
    const currentSelectedMarker = currentSelectedId
      ? markersById.get(currentSelectedId)
      : undefined
    if (currentSelectedMarker) {
      currentSelectedMarker.setImage(createMarkerImage(kakaoGlobal, true))
      previousSelectedIdRef.current = currentSelectedId ?? null
    } else {
      previousSelectedIdRef.current = null
    }
  }, [items, status])

  /*
   * selectedId가 바뀔 때만 실행되어, 이전 선택 마커는 기본 이미지로 되돌리고 새 선택
   * 마커만 선택 이미지로 바꾼다. 마커 전체를 다시 만들지 않으므로 깜빡임이 없다(Finding C).
   * 필터 변경으로 선택된 맛집이 새 items에서 빠진 뒤에도 selectedId가 남아있을 수
   * 있으므로, 마커를 찾지 못하면 조용히 무시한다.
   */
  useEffect(() => {
    const kakaoGlobal = kakaoRef.current
    if (!kakaoGlobal || status !== 'ready') {
      return
    }

    const markersById = markersByIdRef.current
    const previousId = previousSelectedIdRef.current

    if (previousId && previousId !== selectedId) {
      const previousMarker = markersById.get(previousId)
      if (previousMarker) {
        previousMarker.setImage(createMarkerImage(kakaoGlobal, false))
      }
    }

    if (selectedId) {
      const selectedMarker = markersById.get(selectedId)
      if (selectedMarker) {
        selectedMarker.setImage(createMarkerImage(kakaoGlobal, true))
      }
    }

    previousSelectedIdRef.current = selectedId
  }, [selectedId, status])

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
