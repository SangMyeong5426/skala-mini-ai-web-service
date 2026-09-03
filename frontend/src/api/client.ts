import type { ApiError } from '../types/api'
import { MockError, NOT_FOUND, USE_MOCK, mockRequest } from './mock'

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

/** Mock 에 넘길 때만 쓴다. JSON.stringify 한 본문을 되돌린다. */
function parseBody(body: BodyInit | null | undefined): unknown {
  if (typeof body !== 'string') return undefined
  try {
    return JSON.parse(body)
  } catch {
    return undefined
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
  // 백엔드가 붙기 전에는 가짜 서버를 쓴다. VITE_USE_MOCK=true 일 때만 켜진다.
  // 다루지 않는 경로는 mockRequest 가 undefined 를 주므로 아래로 내려가 404 가 난다 —
  // 안 만든 것을 조용히 성공시키지 않는다.
  if (USE_MOCK) {
    const method = init?.method ?? 'GET'
    const mocked = mockRequest(method, path, parseBody(init?.body))

    // 자원이 없다 — 06 이 정의한 정상 오류다. 화면이 다뤄야 하는 상태다.
    if (mocked === NOT_FOUND) {
      throw new ApiFailure(404, 'NOT_FOUND', '찾을 수 없습니다.')
    }
    // 경로를 아직 안 만들었다 — Mock 이 덜 된 것이다. 위와 구분한다.
    if (!mocked) {
      throw new ApiFailure(404, 'MOCK_MISS', `Mock 에 없는 경로입니다: ${method} ${path}`)
    }
    const value = await mocked
    // Mock 이 오류 봉투를 돌려준 경우 — 로그인 실패·중복 가입 등.
    // 실제 서버의 4xx 와 같은 예외로 바꿔서 화면이 한 가지 방식으로만 다루게 한다.
    if (value instanceof MockError) {
      throw new ApiFailure(value.status, value.code, value.message, value.field)
    }
    return value as T
  }

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
