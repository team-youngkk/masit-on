export type RegistrationPreviewDecision = 'READY' | 'DUPLICATE' | 'REVIEW_REQUIRED'

export type RegistrationStepDecision =
  | { action: 'create' }
  | { action: 'skip'; existingId: string }
  | { action: 'blocked' }

export type RegistrationFlowStep = 'restaurant' | 'creator' | 'video' | 'visit'
export type RegistrationResourceKind = 'created' | 'duplicate'
export type RegistrationCompletion =
  | { status: 'success'; resourceId: string; kind: RegistrationResourceKind }
  | { status: 'failure' }

export type RegistrationCompletionTransition = {
  resourceId: string
  nextStep: Exclude<RegistrationFlowStep, 'visit'> | 'visit'
}

/** 성공 응답만 다음 단계로 진행시키고, 같은 단계의 완료 콜백 중복 호출은 무시한다. */
export function registrationCompletionTransition(
  currentStep: RegistrationFlowStep,
  completion: RegistrationCompletion,
  alreadyCompleted: boolean,
): RegistrationCompletionTransition | null {
  if (completion.status !== 'success' || alreadyCompleted) {
    return null
  }

  const resourceId = completion.resourceId.trim()
  if (!resourceId) {
    return null
  }

  if (currentStep === 'visit' || !['created', 'duplicate'].includes(completion.kind)) {
    return null
  }

  const nextStep: Record<Exclude<RegistrationFlowStep, 'visit'>, RegistrationCompletionTransition['nextStep']> = {
    restaurant: 'creator',
    creator: 'video',
    video: 'visit',
  }
  return { resourceId, nextStep: nextStep[currentStep] }
}

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
