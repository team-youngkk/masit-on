'use client'

/*
 * 코스 계산 성공 결과 전용 지도. 순서 마커·실제 자동차 경로 선을 표시한다.
 * 계약: docs/05-specs/api/discovery/restaurant-course-recommendation-api.md 4절
 *       docs/01-requirements/functional-requirements.md FR-COURSE-004
 *       docs/01-requirements/business-rules.md BR-COURSE-005
 *       docs/08-planning/issue-231-course-route-map.md D-231-05
 *
 * `/map` 화면의 KakaoMapView·MapScreen과는 SDK loader(loadKakaoMapsSdk)와 최소 타입만
 * 공유하고 그 밖의 상태는 독립적으로 관리한다. SDK 로딩 실패·키 누락은 이 컴포넌트 내부
 * 상태로만 처리하고 상위 CourseScreen의 순서·거리·시간 텍스트 목록 렌더링에 영향을 주지
 * 않는다.
 */

import { useEffect, useRef, useState } from 'react'

import { loadKakaoMapsSdk } from '@/components/map/KakaoMapView'
import type {
  KakaoGlobal,
  KakaoMap,
  KakaoMarker,
  KakaoMarkerImage,
  KakaoPolyline,
} from '@/components/map/kakao-maps-types'

import {
  availableSegmentPaths,
  buildCourseMarkerSvgDataUrl,
  collectCourseMapPoints,
  computeCourseMapBounds,
  describeCourseMarker,
} from '@/lib/course/course-route-map-state'
import type { CourseRestaurant, CourseSegment } from '@/lib/course/course-screen-state'

import styles from './CourseRouteMap.module.css'

/* 브라우저에 노출되는 식별자이며 비밀키가 아니다(KakaoMapView와 동일한 키 재사용). */
const KAKAO_MAPS_JS_KEY = process.env.NEXT_PUBLIC_KAKAO_MAPS_JS_KEY
const SDK_LOAD_TIMEOUT_MS = 10_000
const DEFAULT_CENTER = { latitude: 37.5665, longitude: 126.978 }

type CourseRouteMapProps = {
  restaurants: CourseRestaurant[]
  segments: CourseSegment[]
  selectedRestaurantId: string | null
  onSelectRestaurant: (id: string) => void
  /* 만료된 결과는 좌표·형상이 최신 조작 대상처럼 보이지 않도록 지도 조작을 잠근다. */
  expired: boolean
}

function buildMarkerImage(
  kakaoGlobal: KakaoGlobal,
  role: CourseRestaurant['role'],
  sequence: number,
  selected: boolean,
): KakaoMarkerImage {
  const visual = describeCourseMarker(role, sequence, selected)
  const svg = buildCourseMarkerSvgDataUrl(visual)
  return new kakaoGlobal.maps.MarkerImage(svg, new kakaoGlobal.maps.Size(36, 44))
}

export function CourseRouteMap({
  restaurants,
  segments,
  selectedRestaurantId,
  onSelectRestaurant,
  expired,
}: CourseRouteMapProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<KakaoMap | null>(null)
  const kakaoRef = useRef<KakaoGlobal | null>(null)
  const markersByIdRef = useRef<Map<string, KakaoMarker>>(new Map())
  const polylinesRef = useRef<KakaoPolyline[]>([])
  const previousSelectedIdRef = useRef<string | null>(null)
  const onSelectRestaurantRef = useRef(onSelectRestaurant)
  const selectedRestaurantIdRef = useRef(selectedRestaurantId)
  const restaurantsRef = useRef(restaurants)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [retryCount, setRetryCount] = useState(0)

  onSelectRestaurantRef.current = onSelectRestaurant
  selectedRestaurantIdRef.current = selectedRestaurantId
  restaurantsRef.current = restaurants

  /* SDK 로딩과 지도 인스턴스 생성. 최초 방문 맛집 좌표를 중심으로 삼고, 이후 영역 맞춤은
   * 마커·경로 effect가 담당한다. */
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
        try {
          kakaoRef.current = kakaoGlobal
          const initialCoordinate = restaurantsRef.current[0]?.coordinate ?? DEFAULT_CENTER
          const center = new kakaoGlobal.maps.LatLng(
            initialCoordinate.latitude,
            initialCoordinate.longitude,
          )
          const map = new kakaoGlobal.maps.Map(containerRef.current, {
            center,
            level: 6,
          })
          mapRef.current = map
          setStatus('ready')
        } catch {
          if (!cancelled) {
            setStatus('error')
          }
        }
      })
      .catch(() => {
        if (!cancelled) {
          setStatus('error')
        }
      })

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [retryCount])

  /*
   * restaurants·segments가 바뀔 때만(= 새 코스 계산 결과) 마커·경로 선을 처음부터 다시
   * 만들고 전체 영역을 다시 맞춘다. selectedRestaurantId는 별도 effect가 담당해 선택
   * 변경만으로 마커 전체를 다시 만들지 않는다(KakaoMapView와 같은 패턴).
   */
  useEffect(() => {
    const kakaoGlobal = kakaoRef.current
    const map = mapRef.current
    if (!kakaoGlobal || !map || status !== 'ready') {
      return
    }

    try {
      for (const marker of markersByIdRef.current.values()) {
        marker.setMap(null)
      }
      for (const polyline of polylinesRef.current) {
        polyline.setMap(null)
      }

      const currentSelectedId = selectedRestaurantIdRef.current
      const markersById = new Map<string, KakaoMarker>()

      for (const restaurant of restaurants) {
        const selected = restaurant.restaurantId === currentSelectedId
        const image = buildMarkerImage(kakaoGlobal, restaurant.role, restaurant.sequence, selected)
        const position = new kakaoGlobal.maps.LatLng(
          restaurant.coordinate.latitude,
          restaurant.coordinate.longitude,
        )
        const marker = new kakaoGlobal.maps.Marker({ position, map, image })
        kakaoGlobal.maps.event.addListener(marker, 'click', () => {
          onSelectRestaurantRef.current(restaurant.restaurantId)
        })
        markersById.set(restaurant.restaurantId, marker)
      }

      markersByIdRef.current = markersById
      previousSelectedIdRef.current = currentSelectedId

      polylinesRef.current = availableSegmentPaths(segments).map((path) => {
        const polylinePath = path.map(
          (point) => new kakaoGlobal.maps.LatLng(point.latitude, point.longitude),
        )
        const polyline = new kakaoGlobal.maps.Polyline({
          path: polylinePath,
          strokeWeight: 4,
          strokeColor: '#2563eb',
          strokeOpacity: 0.85,
          strokeStyle: 'solid',
        })
        polyline.setMap(map)
        return polyline
      })

      const bounds = computeCourseMapBounds(collectCourseMapPoints(restaurants, segments))
      if (bounds) {
        const latLngBounds = new kakaoGlobal.maps.LatLngBounds()
        latLngBounds.extend(new kakaoGlobal.maps.LatLng(bounds.south, bounds.west))
        latLngBounds.extend(new kakaoGlobal.maps.LatLng(bounds.north, bounds.east))
        map.setBounds(latLngBounds)
      }
    } catch {
      setStatus('error')
    }
  }, [restaurants, segments, status])

  /*
   * selectedRestaurantId가 바뀔 때만 실행되어 이전 선택 마커는 기본 이미지로, 새 선택
   * 마커만 강조 이미지로 바꾼다. 필터·후보 변경으로 선택된 맛집이 새 restaurants에서
   * 빠진 뒤에도 selectedRestaurantId가 남아있을 수 있으므로 마커를 찾지 못하면 무시한다.
   */
  useEffect(() => {
    const kakaoGlobal = kakaoRef.current
    if (!kakaoGlobal || status !== 'ready') {
      return
    }

    const markersById = markersByIdRef.current
    const previousId = previousSelectedIdRef.current

    if (previousId && previousId !== selectedRestaurantId) {
      const previousMarker = markersById.get(previousId)
      const previousRestaurant = restaurants.find((item) => item.restaurantId === previousId)
      if (previousMarker && previousRestaurant) {
        previousMarker.setImage(
          buildMarkerImage(kakaoGlobal, previousRestaurant.role, previousRestaurant.sequence, false),
        )
      }
    }

    if (selectedRestaurantId) {
      const selectedMarker = markersById.get(selectedRestaurantId)
      const selectedRestaurant = restaurants.find(
        (item) => item.restaurantId === selectedRestaurantId,
      )
      if (selectedMarker && selectedRestaurant) {
        selectedMarker.setImage(
          buildMarkerImage(
            kakaoGlobal,
            selectedRestaurant.role,
            selectedRestaurant.sequence,
            true,
          ),
        )
      }
    }

    previousSelectedIdRef.current = selectedRestaurantId
  }, [selectedRestaurantId, restaurants, status])

  return (
    <div className={styles.container}>
      <div ref={containerRef} className={styles.map} role="group" aria-label="코스 경로 지도" />
      {status === 'loading' ? (
        <p className={styles.overlay} aria-live="polite">
          지도를 불러오는 중입니다.
        </p>
      ) : null}
      {status === 'error' ? (
        <div className={styles.overlay} role="alert">
          <p>지도를 불러올 수 없습니다. 아래 순서·거리·시간 목록은 계속 확인할 수 있습니다.</p>
          <button
            type="button"
            className={styles.retry}
            onClick={() => setRetryCount((count) => count + 1)}
          >
            다시 시도
          </button>
        </div>
      ) : null}
      {status === 'ready' && expired ? (
        <div className={styles.expiredOverlay} role="status" aria-live="polite">
          <p>이 경로는 만료되어 지도 조작을 사용할 수 없습니다. 새 경로를 조회해 주세요.</p>
        </div>
      ) : null}
    </div>
  )
}
