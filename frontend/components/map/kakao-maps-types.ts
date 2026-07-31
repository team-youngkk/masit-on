/*
 * Kakao Maps JavaScript SDK V3 최소 타입 선언.
 * ADR-MAP-001 6.1: Kakao SDK 타입은 프론트엔드 지도 코드 내부에만 머물러야 한다.
 * 이 파일은 frontend/components/map 밖에서 import하지 않는다.
 */

export type KakaoLatLng = {
  getLat(): number
  getLng(): number
}

export type KakaoLatLngBounds = {
  getSouthWest(): KakaoLatLng
  getNorthEast(): KakaoLatLng
}

export type KakaoMarkerImage = object

export type KakaoMarker = {
  setMap(map: KakaoMap | null): void
  setImage(image: KakaoMarkerImage): void
}

export type KakaoMap = {
  getBounds(): KakaoLatLngBounds
  panTo(position: KakaoLatLng): void
}

export type KakaoEventTarget = KakaoMap | KakaoMarker

export type KakaoMapsNamespace = {
  LatLng: new (lat: number, lng: number) => KakaoLatLng
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
  Size: new (width: number, height: number) => { width: number; height: number }
  event: {
    addListener(
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
