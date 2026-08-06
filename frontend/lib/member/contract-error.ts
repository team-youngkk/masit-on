export type ContractErrorBody = {
  code?: string
  message?: string
  traceId?: string
}

export type ParsedContractError<T extends ContractErrorBody> = { status: number; contract: T }

/**
 * 회원 전용 API는 실패 시 원본 Response를 던지는 관례를 쓴다. 여러 도메인
 * (participation, notification 등)이 각자 계약 오류 타입을 좁혀 쓰되 이
 * 파싱 로직 자체는 공유해서 error-contract.md 파싱 규칙이 도메인마다
 * 따로 갈리지 않게 한다.
 */
export async function parseContractError<T extends ContractErrorBody>(
  reason: unknown,
): Promise<ParsedContractError<T> | null> {
  if (!(reason instanceof Response)) return null
  let contract = {} as T
  try {
    contract = (await reason.json()) as T
  } catch {
    contract = {} as T
  }
  return { status: reason.status, contract }
}
