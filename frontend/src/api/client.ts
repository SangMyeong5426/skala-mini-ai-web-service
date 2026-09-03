import type { ApiError } from '../types/api'

/**
 * fetch 래퍼 하나로 통일한다. 화면마다 fetch 를 직접 부르면 오류 처리가 흩어진다.
 *
 * Base URL 은 .env 의 VITE_API_BASE_URL 에서 읽는다(`http://localhost:8080/api`).
 * 코드에 호스트를 쓰지 않는다 — AI-Ready 원칙 4.
 *
 * 사진 URL 은 여기를 거치지 않는다. `/uploads/**` 는 vite.config.ts 의 프록시가
 * 8080 으로 넘긴다. `/api` 밑이 아니므로 이 함수에 넣지 않는다.
 */
const BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

/** 06 의 오류 봉투를 담은 예외. 화면은 `message` 를 그대로 보여주면 된다. */
export class ApiFailure extends Error {
  // tsconfig 의 erasableSyntaxOnly 때문에 생성자 파라미터 프로퍼티를 쓸 수 없다.
  // 필드를 따로 선언하고 대입한다.
  status: number
  code: string
  field?: string

  constructor(status: number, code: string, message: string, field?: string) {
    super(message)
    this.name = 'ApiFailure'
    this.status = status
    this.code = code
    this.field = field
  }
}

async function toFailure(res: Response): Promise<ApiFailure> {
  // 06 은 모든 오류가 { error: { code, message, field? } } 라고 약속한다.
  // 그래도 방어한다 — 프록시나 서버가 HTML 을 돌려줄 수 있다.
  try {
    const body = (await res.json()) as ApiError
    if (body?.error?.message) {
      return new ApiFailure(res.status, body.error.code, body.error.message, body.error.field)
    }
  } catch {
    /* JSON 이 아니면 아래로 */
  }
  return new ApiFailure(res.status, 'UNKNOWN', `요청이 실패했습니다 (${res.status})`)
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      // FormData 일 때는 boundary 를 브라우저가 정해야 하므로 Content-Type 을 넣지 않는다.
      ...(init?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...init?.headers,
    },
  })

  if (!res.ok) throw await toFailure(res)
  // 204 No Content — 삭제 성공. 본문이 없다.
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'POST',
      body: body instanceof FormData ? body : JSON.stringify(body ?? {}),
    }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  del: (path: string) => request<void>(path, { method: 'DELETE' }),
}
