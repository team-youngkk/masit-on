const markerStyleAssert = require('node:assert/strict')
const markerStyleFs = require('node:fs')
const markerStylePath = require('node:path')
const markerStyleTest = require('node:test')

const markerStyles = markerStyleFs.readFileSync(
  markerStylePath.join(__dirname, 'KakaoMapView.module.css'),
  'utf8',
)

markerStyleTest('지도 마커 프로필 이미지는 기본 핀 안에서 식별 가능한 크기를 사용한다', () => {
  markerStyleAssert.match(
    markerStyles,
    /\.markerImage,\s*\.markerFallback\s*\{[\s\S]*?top:\s*4px;[\s\S]*?left:\s*8px;[\s\S]*?width:\s*28px;[\s\S]*?height:\s*28px;/,
  )
  markerStyleAssert.match(
    markerStyles,
    /\.markerFallback\s*\{\s*top:\s*9px;\s*left:\s*13px;\s*width:\s*18px;\s*height:\s*18px;/,
  )
})

markerStyleTest('좁은 화면에서는 지도 마커 이미지가 핀을 넘지 않도록 한 단계 줄어든다', () => {
  markerStyleAssert.match(
    markerStyles,
    /@media \(max-width: 389px\)\s*\{[\s\S]*?\.markerImage\s*\{[\s\S]*?width:\s*26px;[\s\S]*?height:\s*26px;[\s\S]*?\}[\s\S]*?\.markerFallback\s*\{[\s\S]*?width:\s*16px;[\s\S]*?height:\s*16px;/,
  )
})

markerStyleTest('지도 마커 버튼은 키보드·터치 조작 영역을 확보한다', () => {
  markerStyleAssert.match(
    markerStyles,
    /\.marker\s*\{[\s\S]*?width:\s*var\(--control-min-height\);[\s\S]*?min-width:\s*var\(--control-min-height\);/,
  )
})
