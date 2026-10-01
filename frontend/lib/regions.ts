export type Region = { code: string; name: string }
export type Province = Region & { children: Region[] }

export function decodeRegions(value: unknown): Province[] | null {
  if (!value || typeof value !== 'object' || !('items' in value) || !Array.isArray(value.items)) return null
  const codes = new Set<string>()
  const isRegion = (item: unknown): item is Region => {
    if (!item || typeof item !== 'object' || !('code' in item) || !('name' in item)
      || typeof item.code !== 'string' || !/^\d{10}$/.test(item.code)
      || typeof item.name !== 'string' || !item.name.trim() || codes.has(item.code)) return false
    codes.add(item.code)
    return true
  }
  const provinces: Province[] = []
  for (const item of value.items) {
    if (!isRegion(item) || !('children' in item) || !Array.isArray(item.children) || !item.children.every(isRegion)) return null
    provinces.push({ code: item.code, name: item.name, children: item.children.map(({ code, name }) => ({ code, name })) })
  }
  return provinces
}

/* 기존 district 북마크는 서울 안에서만 해석한다. 다른 시도의 동명 구와 연결하지 않는다. */
export function regionSelection(provinces: readonly Province[], regionCode: string, district = '') {
  const province = regionCode
    ? provinces.find((item) => item.code === regionCode || item.children.some((child) => child.code === regionCode))
    : district ? provinces.find((item) => item.code === '1100000000') : undefined
  const child = regionCode
    ? province?.children.find((item) => item.code === regionCode)
    : province?.children.find((item) => item.name === district)
  return { province, child }
}

export function regionLabels(provinces: readonly Province[]): Record<string, string> {
  return Object.fromEntries(provinces.flatMap((province) => [
    [province.code, province.name],
    ...province.children.map((child) => [child.code, `${province.name} ${child.name}`]),
  ]))
}

export function changeRegionSelection(regionCode: string): { regionCode: string; district: string } {
  return { regionCode, district: '' }
}
