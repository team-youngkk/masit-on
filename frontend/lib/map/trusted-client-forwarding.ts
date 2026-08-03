export function trustedClientForwardingHeaders(
  trustedClientAddress?: string,
): Record<string, string> | undefined {
  return trustedClientAddress
    ? { 'X-Forwarded-For': trustedClientAddress }
    : undefined
}
