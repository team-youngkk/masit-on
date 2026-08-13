export type IdempotencyAttempt = { fingerprint: string; key: string }

export function idempotencyAttempt(
  previous: IdempotencyAttempt | null,
  fingerprint: string,
  generate: () => string,
): IdempotencyAttempt {
  return previous?.fingerprint === fingerprint ? previous : { fingerprint, key: generate() }
}
