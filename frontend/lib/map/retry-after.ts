/*
 * `Retry-After` 헤더(초 단위)를 재조회 가능 시각(epoch ms)으로 변환하는 순수 함수.
 * ADR-MAP-001 6.4: 서버는 429 RATE_LIMIT_EXCEEDED와 초 단위 Retry-After만 반환한다.
 */
export function parseRetryAfterHeader(
  headerValue: string | null | undefined,
  nowMs: number,
): number | null {
  if (headerValue == null) {
    return null
  }

  const seconds = Number(headerValue)
  if (!Number.isFinite(seconds) || seconds < 0) {
    return null
  }

  return nowMs + seconds * 1000
}
