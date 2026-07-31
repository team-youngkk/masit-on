/*
 * 지도 마커·대체 목록의 선택 상태를 계산하는 순수 함수.
 * 계약: docs/04-product/prd/discovery/map-discovery.md 5·6·9절(마커·목록 선택 연동)
 */

export function toggleMapSelection(
  currentSelectedId: string | null,
  clickedId: string,
): string | null {
  return currentSelectedId === clickedId ? null : clickedId
}

export function isMapPointSelected(
  id: string,
  selectedId: string | null,
): boolean {
  return id === selectedId
}

export function findSelectedMapPoint<T extends { id: string }>(
  items: T[],
  selectedId: string | null,
): T | null {
  if (selectedId == null) {
    return null
  }
  return items.find((item) => item.id === selectedId) ?? null
}
