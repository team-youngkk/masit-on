export type AiConfidenceTone = 'danger' | 'warning' | 'success'

const LOW_CONFIDENCE_THRESHOLD = 0.6
const HIGH_CONFIDENCE_THRESHOLD = 0.8

export type AiConfidencePresentation = { label: string; percentage: number }

/** API confidence(0..1)를 관리자 UI 배지 톤으로 표현하는 정책이다. */
export function aiConfidenceTone(confidence: number): AiConfidenceTone {
  if (confidence < LOW_CONFIDENCE_THRESHOLD) return 'danger'
  if (confidence < HIGH_CONFIDENCE_THRESHOLD) return 'warning'
  return 'success'
}

export function aiConfidencePresentation(confidence: number): AiConfidencePresentation {
  if (!Number.isFinite(confidence)) return { label: '확인 필요', percentage: 0 }
  if (confidence < LOW_CONFIDENCE_THRESHOLD) return { label: '낮음', percentage: Math.round(confidence * 100) }
  if (confidence < HIGH_CONFIDENCE_THRESHOLD) return { label: '중간', percentage: Math.round(confidence * 100) }
  return { label: '높음', percentage: Math.round(confidence * 100) }
}
