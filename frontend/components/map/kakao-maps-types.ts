/*
 * Kakao Maps JavaScript SDK V3 최소 타입 선언.
 * ADR-MAP-001: Kakao SDK 타입은 프론트엔드 지도 코드 내부에만 머물러야 한다.
 * 이 파일은 원칙적으로 frontend/components/map 밖에서 import하지 않는다.
 * 예외: docs/08-planning/issue-231-course-route-map.md `D-231-05`(이슈 #233 구현에서 확정)에
 * 따라 SDK loader·이 최소 타입만 `frontend/app/course/CourseRouteMap.tsx`에서 공통으로
 * 재사용한다. 그 밖의 지도 표시 상태(state)는 이 파일과 무관하게 각자 분리해서 유지한다.
 */

export type KakaoLatLng = {
  getLat(): number
  getLng(): number
}

export type KakaoMarkerImage = object

export type KakaoMarker = {
  setMap(map: KakaoMap | null): void
  setImage(image: KakaoMarkerImage): void
}

export type KakaoLatLngBounds = {
  extend(latlng: KakaoLatLng): void
}

export type KakaoPolyline = {
  setMap(map: KakaoMap | null): void
  setPath(path: KakaoLatLng[]): void
}

export type KakaoMap = {
  panTo(position: KakaoLatLng): void
  setBounds(bounds: KakaoLatLngBounds): void
}

export type KakaoEventTarget = KakaoMap | KakaoMarker

export type KakaoMapsNamespace = {
  LatLng: new (lat: number, lng: number) => KakaoLatLng
  LatLngBounds: new () => KakaoLatLngBounds
  Map: new (
    container: HTMLElement,
    options: { center: KakaoLatLng; level?: number },
  ) => KakaoMap
  Marker: new (options: {
    position: KakaoLatLng
    map?: KakaoMap
    image?: KakaoMarkerImage
  }) => KakaoMarker
  MarkerImage: new (
    src: string,
    size: { width: number; height: number },
  ) => KakaoMarkerImage
  Polyline: new (options: {
    path: KakaoLatLng[]
    strokeWeight?: number
    strokeColor?: string
    strokeOpacity?: number
    strokeStyle?: string
  }) => KakaoPolyline
  Size: new (width: number, height: number) => { width: number; height: number }
  event: {
    addListener(
      target: KakaoEventTarget,
      type: string,
      handler: () => void,
    ): void
    removeListener(
      target: KakaoEventTarget,
      type: string,
      handler: () => void,
    ): void
  }
  load(callback: () => void): void
}

export type KakaoGlobal = {
  maps: KakaoMapsNamespace
}
