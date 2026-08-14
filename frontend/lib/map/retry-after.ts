/*
 * `Retry-After` 헤더(초 단위)를 재조회 가능 시각(epoch ms)으로 변환하는 순수 함수.
 * ADR-MAP-001 6.4: 서버는 429 RATE_LIMIT_EXCEEDED와 초 단위 Retry-After만 반환한다.
 * 프록시 등에서 헤더가 유실되거나 손상돼도 429 직후 즉시 재요청하지 않도록 1초를 대기한다.
 */
const FALLBACK_RETRY_AFTER_MS = 1_000
const MAX_TIMER_DELAY_MS = 2_147_483_647

export function parseRetryAfterHeader(
  headerValue: string | null | undefined,
  nowMs: number,
): number {
  if (headerValue == null) {
    return nowMs + FALLBACK_RETRY_AFTER_MS
  }

  const normalized = headerValue.trim()
  const seconds = Number(normalized)
  const delayMs = seconds * 1000
  if (
    normalized.length === 0
    || !Number.isFinite(seconds)
    || seconds < 0
    || !Number.isFinite(delayMs)
    || delayMs > MAX_TIMER_DELAY_MS
  ) {
    return nowMs + FALLBACK_RETRY_AFTER_MS
  }

  return nowMs + delayMs
}
