import assert from 'node:assert/strict'
import test from 'node:test'

import {
  availableSegmentPaths,
  buildCourseMarkerSvgDataUrl,
  collectCourseMapPoints,
  computeCourseMapBounds,
  describeCourseMarker,
  toggleCourseMapSelection,
} from './course-route-map-state.ts'

test('역할별 마커 색은 선택 여부와 무관하게 서로 다르다', () => {
  const start = describeCourseMarker('START', 1, false)
  const waypoint = describeCourseMarker('WAYPOINT', 2, false)
  const destination = describeCourseMarker('DESTINATION', 3, false)

  assert.notEqual(start.fillColor, waypoint.fillColor)
  assert.notEqual(waypoint.fillColor, destination.fillColor)
  assert.notEqual(start.fillColor, destination.fillColor)
})

test('선택된 마커는 역할과 무관하게 같은 강조색을 쓴다', () => {
  const selectedStart = describeCourseMarker('START', 1, true)
  const selectedDestination = describeCourseMarker('DESTINATION', 3, true)

  assert.equal(selectedStart.fillColor, selectedDestination.fillColor)
})

test('마커 라벨은 방문 순서 번호 문자열이다', () => {
  assert.equal(describeCourseMarker('START', 1, false).label, '1')
  assert.equal(describeCourseMarker('WAYPOINT', 4, true).label, '4')
})

test('마커 SVG data URL에 채우기 색과 순서 라벨이 포함된다', () => {
  const svg = buildCourseMarkerSvgDataUrl({ fillColor: '%2316a34a', label: '2' })
  assert.match(svg, /^data:image\/svg\+xml,/)
  assert.match(svg, /%2316a34a/)
  assert.match(svg, /%3E2%3C\/text%3E/)
})

test('지도 영역 계산 대상에는 방문지 좌표와 AVAILABLE 구간 경로만 포함한다', () => {
  const restaurants = [
    { coordinate: { latitude: 37.5665, longitude: 126.978 } },
    { coordinate: { latitude: 37.5601, longitude: 126.985 } },
  ]
  const segments = [
    {
      shapeStatus: 'AVAILABLE' as const,
      path: [{ latitude: 37.566, longitude: 126.9795 }],
    },
    {
      shapeStatus: 'MISSING' as const,
      path: [],
    },
  ]

  const points = collectCourseMapPoints(restaurants, segments)

  assert.deepEqual(points, [
    { latitude: 37.5665, longitude: 126.978 },
    { latitude: 37.5601, longitude: 126.985 },
    { latitude: 37.566, longitude: 126.9795 },
  ])
})

test('좌표가 없으면 지도 영역을 계산하지 않는다', () => {
  assert.equal(computeCourseMapBounds([]), null)
})

test('좌표 목록을 모두 포함하는 최소 사각 영역을 계산한다', () => {
  const bounds = computeCourseMapBounds([
    { latitude: 37.5665, longitude: 126.978 },
    { latitude: 37.5601, longitude: 126.985 },
    { latitude: 37.57, longitude: 126.97 },
  ])

  assert.deepEqual(bounds, {
    south: 37.5601,
    north: 37.57,
    west: 126.97,
    east: 126.985,
  })
})

test('점이 하나면 남·북·동·서 경계가 모두 그 점과 같다', () => {
  const bounds = computeCourseMapBounds([{ latitude: 37.5665, longitude: 126.978 }])
  assert.deepEqual(bounds, { south: 37.5665, north: 37.5665, west: 126.978, east: 126.978 })
})

test('shapeStatus가 AVAILABLE이고 path가 있는 구간만 경로 선을 그린다(BR-COURSE-005)', () => {
  const segments = [
    {
      shapeStatus: 'AVAILABLE' as const,
      path: [
        { latitude: 37.5665, longitude: 126.978 },
        { latitude: 37.5651, longitude: 126.9812 },
      ],
    },
    { shapeStatus: 'MISSING' as const, path: [] },
  ]

  const paths = availableSegmentPaths(segments)

  assert.equal(paths.length, 1)
  assert.deepEqual(paths[0], [
    { latitude: 37.5665, longitude: 126.978 },
    { latitude: 37.5651, longitude: 126.9812 },
  ])
})

test('AVAILABLE이어도 path가 빈 배열이면 그릴 선이 없다', () => {
  const paths = availableSegmentPaths([{ shapeStatus: 'AVAILABLE' as const, path: [] }])
  assert.deepEqual(paths, [])
})

test('선택되지 않은 항목을 클릭하면 그 항목이 선택된다', () => {
  assert.equal(toggleCourseMapSelection(null, 'r1'), 'r1')
  assert.equal(toggleCourseMapSelection('r1', 'r2'), 'r2')
})

test('선택된 항목을 다시 클릭하면 선택이 해제된다(지도·목록 강조 동기화)', () => {
  assert.equal(toggleCourseMapSelection('r1', 'r1'), null)
})
