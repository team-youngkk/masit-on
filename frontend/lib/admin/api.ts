'use client'

import { authenticatedFetch } from './auth.ts'

export type ApiFieldError = {
  field: string
  reason: string
}

type ApiErrorBody = {
  code?: string
  message?: string
  errors?: ApiFieldError[]
  traceId?: string
}

export class AdminApiError extends Error {
  constructor(
    readonly status: number,
    readonly code?: string,
    readonly fieldErrors: ApiFieldError[] = [],
    readonly traceId?: string,
    message = '요청을 처리하지 못했습니다.',
  ) {
    super(message)
    this.name = 'AdminApiError'
  }
}

async function errorFrom(response: Response): Promise<AdminApiError> {
  let body: ApiErrorBody | null = null
  try {
    body = (await response.json()) as ApiErrorBody
  } catch {
    // 응답 본문이 없는 네트워크·프록시 오류도 상태 코드로 안내한다.
  }

  return new AdminApiError(
    response.status,
    body?.code,
    body?.errors ?? [],
    body?.traceId,
    body?.message ?? '요청을 처리하지 못했습니다.',
  )
}

export async function adminJson<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await authenticatedFetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init.headers,
    },
  })

  if (!response.ok) {
    throw await errorFrom(response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export function messageFor(error: unknown): string {
  if (!(error instanceof AdminApiError)) {
    return '네트워크 연결을 확인한 뒤 다시 시도해 주세요.'
  }

  if (error.code?.includes('CONFIRMATION') || error.code?.includes('EXPIRED')) {
    return '확인 토큰이 만료되었습니다. 미리보기부터 다시 진행해 주세요.'
  }

  if (error.status >= 500) {
    return error.traceId
      ? `요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요. (문의 ID: ${error.traceId})`
      : '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  }

  return error.traceId
    ? `${error.message} (문의 ID: ${error.traceId})`
    : error.message
}

export function fieldErrorsFor(error: unknown): Record<string, string> {
  if (!(error instanceof AdminApiError)) {
    return {}
  }

  return Object.fromEntries(error.fieldErrors.map(({ field, reason }) => [field, reason]))
}
