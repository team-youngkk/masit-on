export type RegistrationPreviewDecision = 'READY' | 'DUPLICATE' | 'REVIEW_REQUIRED'

export type RegistrationStepDecision =
  | { action: 'create' }
  | { action: 'skip'; existingId: string }
  | { action: 'blocked' }

/**
 * 등록 미리보기 판정에서 다음 단계를 결정한다.
 * READY는 새로 생성하고, DUPLICATE는 기존 자원 id로 건너뛰고,
 * REVIEW_REQUIRED이거나 DUPLICATE인데 기존 id를 확인할 수 없으면 진행을 막는다.
 */
export function registrationStepDecision(preview: {
  decision: RegistrationPreviewDecision
  existingResource: Record<string, unknown> | null
}): RegistrationStepDecision {
  if (preview.decision === 'READY') {
    return { action: 'create' }
  }

  if (preview.decision === 'DUPLICATE') {
    const id = preview.existingResource?.id
    const normalizedId = typeof id === 'string' ? id.trim() : ''
    return normalizedId ? { action: 'skip', existingId: normalizedId } : { action: 'blocked' }
  }

  return { action: 'blocked' }
}
