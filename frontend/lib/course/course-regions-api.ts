'use client'

import { decodeRegions, type Province } from '../regions.ts'

const FALLBACK_ERROR_MESSAGE =
  '지역 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

type ApiErrorBody = {
  message?: string
  traceId?: string
}

export type CourseRegionsResult =
  | { ok: true; items: Province[] }
  | { ok: false; message: string; traceId?: string }

/* 클라이언트 코스 화면은 Next.js /api rewrite를 거치는 상대 경로로 지역 마스터를 읽는다. */
export async function fetchCourseRegions(signal?: AbortSignal): Promise<CourseRegionsResult> {
  let response: Response
  try {
    response = await fetch('/api/regions', { cache: 'no-store', signal })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    return { ok: false, message: FALLBACK_ERROR_MESSAGE }
  }

  if (!response.ok) {
    const body = await readErrorBody(response)
    return {
      ok: false,
      message: body?.message ?? FALLBACK_ERROR_MESSAGE,
      traceId: body?.traceId,
    }
  }

  try {
    const items = decodeRegions(await response.json())
    return items ? { ok: true, items } : { ok: false, message: FALLBACK_ERROR_MESSAGE }
  } catch {
    return { ok: false, message: FALLBACK_ERROR_MESSAGE }
  }
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    return (await response.json()) as ApiErrorBody
  } catch {
    return null
  }
}
