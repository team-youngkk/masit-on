/*
 * `/course` 결과 지도(CourseRouteMap) 전용 순수 로직.
 * Kakao Maps SDK 인스턴스 없이 테스트할 수 있도록 마커 시각 구성·지도 영역 계산·선택 동기화만
 * 다루고, 실제 SDK 호출(Marker·Polyline·LatLngBounds 생성)은 컴포넌트에서만 수행한다.
 * 계약: docs/05-specs/api/discovery/restaurant-course-recommendation-api.md 4절
 *       docs/01-requirements/functional-requirements.md FR-COURSE-004
 *       docs/01-requirements/business-rules.md BR-COURSE-005
 *       docs/08-planning/issue-231-course-route-map.md D-231-05(코스 지도 상태는 /map과 분리)
 */

import type {
  CourseCoordinate,
  CourseRestaurantRole,
  CourseSegment,
} from './course-screen-state.ts'

export type CourseMapBounds = {
  south: number
  north: number
  west: number
  east: number
}

export type CourseMarkerVisual = {
  fillColor: string
  label: string
}

/* 역할별 채우기 색은 선택 여부와 무관하게 항상 구분되고, 선택되면 강조색으로 바뀐다. */
const ROLE_FILL_COLORS: Record<CourseRestaurantRole, string> = {
  START: '%2316a34a',
  WAYPOINT: '%232563eb',
  DESTINATION: '%23dc2626',
}

const SELECTED_FILL_COLOR = '%23f59e0b'

/* 마커는 항상 방문 순서 번호를 라벨로 쓴다. 역할은 색으로만 구분하고 별도 아이콘을 추가하지 않는다. */
export function describeCourseMarker(
  role: CourseRestaurantRole,
  sequence: number,
  selected: boolean,
): CourseMarkerVisual {
  return {
    fillColor: selected ? SELECTED_FILL_COLOR : ROLE_FILL_COLORS[role],
    label: String(sequence),
  }
}

/* 외부 이미지 자산을 추가하지 않는 최소 SVG 핀. 순서 번호를 흰 원 위에 표시한다. */
export function buildCourseMarkerSvgDataUrl(visual: CourseMarkerVisual): string {
  const { fillColor, label } = visual
  return (
    `data:image/svg+xml,` +
    `%3Csvg xmlns='http://www.w3.org/2000/svg' width='36' height='44' viewBox='0 0 36 44'%3E` +
    `%3Cpath fill='${fillColor}' d='M18 0C8.059 0 0 8.059 0 18c0 13.5 18 26 18 26s18-12.5 18-26C36 8.059 27.941 0 18 0z'/%3E` +
    `%3Ccircle cx='18' cy='18' r='11' fill='white'/%3E` +
    `%3Ctext x='18' y='23' font-family='sans-serif' font-size='13' font-weight='700' ` +
    `text-anchor='middle' fill='${fillColor}'%3E${label}%3C/text%3E` +
    `%3C/svg%3E`
  )
}

/*
 * 마커 좌표와, 형상이 제공된(AVAILABLE) 구간의 경로 좌표만 지도 영역 계산에 사용한다.
 * 형상이 없는(MISSING) 구간은 직선으로 대체하지 않으므로(BR-COURSE-005) 영역 계산에도
 * 포함하지 않는다.
 */
export function collectCourseMapPoints(
  restaurants: { coordinate: CourseCoordinate }[],
  segments: Pick<CourseSegment, 'shapeStatus' | 'path'>[],
): CourseCoordinate[] {
  const points: CourseCoordinate[] = restaurants.map((restaurant) => restaurant.coordinate)
  for (const segment of segments) {
    if (segment.shapeStatus === 'AVAILABLE') {
      points.push(...segment.path)
    }
  }
  return points
}

/* 좌표 목록을 모두 포함하는 최소 사각 영역을 계산한다. 좌표가 없으면 null이다. */
export function computeCourseMapBounds(points: CourseCoordinate[]): CourseMapBounds | null {
  if (points.length === 0) {
    return null
  }

  let south = points[0].latitude
  let north = points[0].latitude
  let west = points[0].longitude
  let east = points[0].longitude

  for (const point of points) {
    south = Math.min(south, point.latitude)
    north = Math.max(north, point.latitude)
    west = Math.min(west, point.longitude)
    east = Math.max(east, point.longitude)
  }

  return { south, north, west, east }
}

/*
 * 실제 경로 선을 그릴 구간만 반환한다. `shapeStatus`가 MISSING이면 거리·시간이 정상이어도
 * 직선 대체 없이 그 구간은 그리지 않는다(FR-COURSE-004, BR-COURSE-005).
 */
export function availableSegmentPaths(
  segments: Pick<CourseSegment, 'shapeStatus' | 'path'>[],
): CourseCoordinate[][] {
  return segments
    .filter((segment) => segment.shapeStatus === 'AVAILABLE' && segment.path.length > 0)
    .map((segment) => segment.path)
}

/*
 * 마커·목록 클릭 모두 이 토글을 거친다. 같은 항목을 다시 클릭하면 선택을 해제해 지도와
 * 목록의 강조 상태가 항상 일치하게 한다.
 */
export function toggleCourseMapSelection(
  currentSelectedId: string | null,
  clickedId: string,
): string | null {
  return currentSelectedId === clickedId ? null : clickedId
}
