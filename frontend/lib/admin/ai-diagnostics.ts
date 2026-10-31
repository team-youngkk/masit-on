export type AiDiagnostic = {
  version: number
  reasonCode: string
  service: string
  stage: string
  httpStatus: number | null
  providerCode: string | null
  missingFields: string[]
  observedAt: string | null
  traceId: string | null
  causeCode?: string | null
}

const reasonLabels: Record<string, string> = {
  APPLICATION_QUOTA_EXHAUSTED: '맛잇온 호출 한도에 도달했습니다.',
  PROVIDER_AUTHENTICATION_FAILED: 'Gemini 인증에 실패했습니다.',
  PROVIDER_PAYMENT_REQUIRED: 'Gemini API 결제가 필요합니다.',
  PROVIDER_ACCESS_DENIED: 'Gemini 접근이 거부됐습니다.',
  PROVIDER_CONFIGURATION_OR_POLICY_BLOCKED: 'Gemini 호출이 설정 또는 정책에 의해 차단됐습니다.',
  PROVIDER_RATE_LIMITED: 'Gemini 요청 한도에 도달했습니다.',
  PROVIDER_REQUEST_REJECTED: 'Gemini 요청이 거부됐습니다.',
  PROVIDER_RESOURCE_NOT_FOUND: 'Gemini 모델 또는 자원을 찾지 못했습니다.',
  PROVIDER_SERVER_ERROR: 'Gemini 서버 오류가 발생했습니다.',
  EXTERNAL_TIMEOUT: '외부 서비스 응답 시간이 초과됐습니다.',
  EXTERNAL_NETWORK_ERROR: '외부 서비스와 통신하지 못했습니다.',
  RESPONSE_INVALID: '외부 서비스 응답을 검증하지 못했습니다.',
  INTERNAL_PROCESSING_ERROR: '내부 처리 오류가 발생했습니다.',
  DETAIL_NOT_RECORDED: '과거 작업의 세부 원인이 기록되지 않았습니다.',
  UNKNOWN_FAILURE: '실패 원인을 확인해야 합니다.',
  PLACE_NOT_FOUND: '조건을 만족하는 Kakao 장소를 찾지 못했습니다.',
  PLACE_AMBIGUOUS: '조건을 만족하는 Kakao 장소가 둘 이상입니다.',
  CATEGORY_UNRESOLVED: '대표 음식 카테고리 근거를 찾지 못했습니다.',
  MISSING_REQUIRED_FIELD: '등록에 필요한 필수 후보가 없습니다.',
  VISIT_EVIDENCE_REQUIRED: '방문 근거가 부족합니다.',
  DUPLICATE_CONFLICT: '같은 맛집·방문 관계가 이미 등록돼 있습니다.',
  EXTERNAL_SERVICE_ERROR: '외부 조회에 실패했습니다.',
}

const actionLabels: Record<string, string> = {
  APPLICATION_QUOTA_EXHAUSTED: '맛잇온에 적용된 호출 한도와 시간 구간을 확인해 주세요.',
  PROVIDER_AUTHENTICATION_FAILED: 'Gemini API 키 설정을 확인해 주세요.',
  PROVIDER_PAYMENT_REQUIRED: 'Gemini API 프로젝트의 결제 상태를 확인해 주세요.',
  PROVIDER_ACCESS_DENIED: 'Gemini API 프로젝트의 권한과 이용 상태를 확인해 주세요.',
  PROVIDER_CONFIGURATION_OR_POLICY_BLOCKED: 'AI 호출 설정과 무료 등급 검증 상태를 확인해 주세요.',
  PROVIDER_RATE_LIMITED: 'Gemini API 프로젝트에 적용된 호출 한도를 확인해 주세요.',
  PROVIDER_REQUEST_REJECTED: '요청 형식과 프로젝트 조건을 확인해 주세요.',
  PROVIDER_RESOURCE_NOT_FOUND: '모델명과 요청 경로를 확인해 주세요.',
  PROVIDER_SERVER_ERROR: '일시적인 오류인지 확인한 뒤 기존 정책에 따라 재시도해 주세요.',
  EXTERNAL_TIMEOUT: '일시적인 오류인지 확인한 뒤 기존 정책에 따라 재시도해 주세요.',
  EXTERNAL_NETWORK_ERROR: '외부 서비스 연결 상태를 확인해 주세요.',
  RESPONSE_INVALID: '응답 형식과 서버 기록을 확인해 주세요.',
  INTERNAL_PROCESSING_ERROR: '추적 ID로 서버 기록을 확인해 주세요.',
  DETAIL_NOT_RECORDED: '기존 오류 범주와 시도 이력을 확인해 주세요.',
  MISSING_REQUIRED_FIELD: '영상 근거와 보완 텍스트를 확인한 뒤 재추출하거나 기존 수동 등록을 이용해 주세요.',
  DUPLICATE_CONFLICT: '기존에 등록된 자원을 확인해 주세요.',
}

const fieldLabels: Record<string, string> = {
  restaurantName: '식당 이름', address: '주소', location: '위치', menu: '메뉴', visitEvidence: '방문 근거',
}

const serviceLabels: Record<string, string> = {
  GEMINI: 'Gemini', KAKAO: 'Kakao', YOUTUBE: 'YouTube', APPLICATION: '맛잇온',
}

export function parseAiDiagnostic(value: unknown): AiDiagnostic | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const raw = value as Record<string, unknown>
  if (raw.version !== 1 || typeof raw.reasonCode !== 'string') return null
  return {
    version: 1,
    reasonCode: raw.reasonCode,
    service: typeof raw.service === 'string' ? raw.service : 'UNKNOWN',
    stage: typeof raw.stage === 'string' ? raw.stage : 'UNKNOWN',
    httpStatus: typeof raw.httpStatus === 'number' && Number.isInteger(raw.httpStatus) && raw.httpStatus >= 100 && raw.httpStatus <= 599 ? raw.httpStatus : null,
    providerCode: typeof raw.providerCode === 'string' ? raw.providerCode : null,
    missingFields: Array.isArray(raw.missingFields) ? raw.missingFields.filter((field): field is string => typeof field === 'string').slice(0, 16) : [],
    observedAt: typeof raw.observedAt === 'string' ? raw.observedAt : null,
    traceId: typeof raw.traceId === 'string' ? raw.traceId : null,
    causeCode: typeof raw.causeCode === 'string' ? raw.causeCode : null,
  }
}

export function aiDiagnosticLabel(diagnostic: AiDiagnostic | null | undefined, legacyCategory?: string | null): string | null {
  if (!diagnostic) return legacyCategory ? `${reasonLabels.DETAIL_NOT_RECORDED} 기존 범주: ${/^[A-Z][A-Z0-9_]{0,63}$/.test(legacyCategory) ? legacyCategory : '확인 불가'}` : null
  if (diagnostic.version !== 1 || !Object.hasOwn(reasonLabels, diagnostic.reasonCode)) return '지원되지 않는 진단 코드입니다. 서버 기록을 확인해 주세요.'
  const fields = [...new Set(diagnostic.missingFields)].map((field) => fieldLabels[field] ?? field)
  const reason = diagnostic.reasonCode === 'MISSING_REQUIRED_FIELD' && fields.length
    ? `${fields.join('·')} 후보가 없습니다.` : reasonLabels[diagnostic.reasonCode]
  const evidence = [diagnostic.httpStatus ? `HTTP ${diagnostic.httpStatus}` : null,
    diagnostic.providerCode && /^[A-Z][A-Z0-9_]{0,63}$/.test(diagnostic.providerCode) ? diagnostic.providerCode : null,
    diagnostic.service !== 'UNKNOWN' && diagnostic.reasonCode === 'EXTERNAL_SERVICE_ERROR' ? `${serviceLabels[diagnostic.service] ?? '외부 서비스'} 조회` : null,
    diagnostic.causeCode && Object.hasOwn(reasonLabels, diagnostic.causeCode) ? reasonLabels[diagnostic.causeCode] : null,
  ].filter(Boolean)
  return [reason, ...evidence].join(' · ')
}

export function aiDiagnosticAction(diagnostic: AiDiagnostic | null | undefined): string | null {
  if (!diagnostic || diagnostic.version !== 1 || !Object.hasOwn(reasonLabels, diagnostic.reasonCode)) return null
  return actionLabels[diagnostic.reasonCode] ?? null
}
