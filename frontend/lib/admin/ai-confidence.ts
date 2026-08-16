export type AiConfidenceTone = 'danger' | 'warning' | 'success'

const LOW_CONFIDENCE_THRESHOLD = 0.6
const HIGH_CONFIDENCE_THRESHOLD = 0.8

/** API confidence(0..1)를 관리자 UI 배지 톤으로 표현하는 정책이다. */
export function aiConfidenceTone(confidence: number): AiConfidenceTone {
  if (confidence < LOW_CONFIDENCE_THRESHOLD) return 'danger'
  if (confidence < HIGH_CONFIDENCE_THRESHOLD) return 'warning'
  return 'success'
}
