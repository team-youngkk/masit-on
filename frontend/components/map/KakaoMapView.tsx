'use client'

import { useEffect, useRef, useState } from 'react'

import type { MapBounds } from '@/lib/map/map-points-query'
import type { MapPointItem } from '@/lib/map/map-points-response'
import { isSafeHttpsUrl } from '@/lib/map/selected-creator-profile-image'

import type { KakaoCustomOverlay, KakaoGlobal, KakaoMap } from './kakao-maps-types'

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
  selectedCreatorProfileImageUrl: string | null
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

/* 외부 이미지는 SVG data URL 안에 넣지 않고 일반 HTML img로 로드한다. */
function createMarkerContent(
  item: MapPointItem,
  selected: boolean,
  selectedCreatorProfileImageUrl: string | null,
  onSelect: (id: string) => void,
): HTMLElement {
  const marker = document.createElement('button')
  marker.type = 'button'
  marker.className = styles.marker
  marker.setAttribute('aria-label', `${item.name} 지도에서 선택`)
  marker.setAttribute('aria-pressed', String(selected))
  marker.addEventListener('click', (event) => {
    event.stopPropagation()
    onSelect(item.id)
  })

  const background = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  background.setAttribute('class', styles.markerBackground)
  background.setAttribute('viewBox', '0 0 36 48')
  background.setAttribute('aria-hidden', 'true')

  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path')
  path.setAttribute('fill', selected ? '#16a34a' : '#6f6a63')
  path.setAttribute('d', 'M18 0C8.06 0 0 8.06 0 18c0 13.5 18 30 18 30s18-16.5 18-30C36 8.06 27.94 0 18 0Z')
  background.append(path)
  marker.append(background)

  const profileImageUrl = item.creatorProfileImageUrl ?? selectedCreatorProfileImageUrl
  if (isSafeHttpsUrl(profileImageUrl)) {
    const fallback = createMarkerFallback()
    const image = document.createElement('img')
    image.className = styles.markerImage
    image.alt = ''
    image.hidden = true
    image.addEventListener('load', () => {
      image.hidden = false
      fallback.replaceWith(image)
    }, { once: true })
    image.addEventListener('error', () => {
      image.remove()
    }, { once: true })
    marker.append(fallback)
    marker.append(image)
    image.src = profileImageUrl
  } else {
    marker.append(createMarkerFallback())
  }

  return marker
}

function createMarkerFallback(): HTMLSpanElement {
  const fallback = document.createElement('span')
  fallback.className = styles.markerFallback
  fallback.setAttribute('aria-hidden', 'true')
  return fallback
}

function createMarkerOverlay(
  kakaoGlobal: KakaoGlobal,
  map: KakaoMap,
  item: MapPointItem,
  selected: boolean,
  selectedCreatorProfileImageUrl: string | null,
  onSelect: (id: string) => void,
): KakaoCustomOverlay {
  const position = new kakaoGlobal.maps.LatLng(
    item.coordinate.latitude,
    item.coordinate.longitude,
  )

  return new kakaoGlobal.maps.CustomOverlay({
    position,
    map,
    content: createMarkerContent(item, selected, selectedCreatorProfileImageUrl, onSelect),
    yAnchor: 1,
    clickable: true,
  })
}

export function KakaoMapView({
  items,
  selectedId,
  selectedCreatorProfileImageUrl,
  fallbackBounds,
  onSelect,
}: KakaoMapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<KakaoMap | null>(null)
  const kakaoRef = useRef<KakaoGlobal | null>(null)
  const overlaysRef = useRef<KakaoCustomOverlay[]>([])
  const overlaysByIdRef = useRef<Map<string, KakaoCustomOverlay>>(new Map())
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
   * items가 바뀔 때만 오버레이 집합을 처음부터 다시 만든다. selectedId를 의존성에 넣지 않아
   * 마커 선택/해제만으로는(최대 200개까지 있을 수 있는) 전체 오버레이를 지우고 다시 만들지
   * 않는다(Finding C). 각 마커는 응답에 포함된 대표 채널 이미지로 만들고, 현재 selectedId에
   * 해당하는 마커가 있으면 즉시 선택 이미지로 되돌려 items 갱신 전후로 선택 표시가 유지되게 한다.
   */
  useEffect(() => {
    const kakaoGlobal = kakaoRef.current
    const map = mapRef.current
    if (!kakaoGlobal || !map || status !== 'ready') {
      return
    }

    for (const overlay of overlaysRef.current) {
      overlay.setMap(null)
    }

    const overlaysById = new Map<string, KakaoCustomOverlay>()

    overlaysRef.current = items.map((item) => {
      const overlay = createMarkerOverlay(
        kakaoGlobal,
        map,
        item,
        selectedIdRef.current === item.id,
        selectedCreatorProfileImageUrl,
        (id) => onSelectRef.current(id),
      )
      overlaysById.set(item.id, overlay)
      return overlay
    })

    overlaysByIdRef.current = overlaysById
    previousSelectedIdRef.current = selectedIdRef.current

    return () => {
      for (const overlay of overlaysRef.current) {
        overlay.setMap(null)
      }
      overlaysRef.current = []
      overlaysByIdRef.current = new Map()
    }
  }, [items, selectedCreatorProfileImageUrl, status])

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

    const overlaysById = overlaysByIdRef.current
    const previousId = previousSelectedIdRef.current

    if (previousId && previousId !== selectedId) {
      const previousOverlay = overlaysById.get(previousId)
      const previousItem = items.find((item) => item.id === previousId)
      if (previousOverlay && previousItem) {
        previousOverlay.setContent(
          createMarkerContent(
            previousItem,
            false,
            selectedCreatorProfileImageUrl,
            (id) => onSelectRef.current(id),
          ),
        )
      }
    }

    if (selectedId) {
      const selectedOverlay = overlaysById.get(selectedId)
      const selectedItem = items.find((item) => item.id === selectedId)
      if (selectedOverlay && selectedItem) {
        selectedOverlay.setContent(
          createMarkerContent(
            selectedItem,
            true,
            selectedCreatorProfileImageUrl,
            (id) => onSelectRef.current(id),
          ),
        )
      }
    }

    previousSelectedIdRef.current = selectedId
  }, [items, selectedId, selectedCreatorProfileImageUrl, status])

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
