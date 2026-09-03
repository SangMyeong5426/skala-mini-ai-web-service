import type { ApiError, SessionResponse } from '../types/api'
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

// 익명 세션을 동시에 둘 만들면 쿠키와 CSRF 토큰이 엇갈린다. 모든 호출 경로가 이 요청을 공유한다.
let sessionRequest: Promise<SessionResponse> | null = null
export const loadSessionOnce = () => {
  if (!sessionRequest) {
    sessionRequest = request<SessionResponse>('/auth/session').finally(() => { sessionRequest = null })
  }
  return sessionRequest
}

/**
 * CSRF 토큰. <b>메모리에만 둔다</b> — 06 이 그렇게 못박았다.
 *
 * `GET /auth/session` 이 줄 때마다 갱신하고, 바꾸는 요청(POST·PATCH·DELETE)에
 * `X-CSRF-TOKEN` 으로 싣는다. 로그인·로그아웃 뒤에는 세션을 다시 조회해
 * 새 토큰을 받는다.
 */
let csrf: string | null = null
export const setCsrfToken = (token: string | null) => { csrf = token }

/**
 * 세션이 끊겼을 때 부를 것. <b>AuthProvider 가 걸어 둔다.</b>
 *
 * 이게 없으면 30분 세션 만료 후에도 user 가 남아 보호 화면을 계속 통과하고,
 * 폴링이 받은 401 은 "AI 작업 실패" 로 뭉개진다. 06:284 는 401 을 로그인 화면
 * 전환 신호로, 수용 기준은 "세션 만료를 AI 작업의 FAILED 로 바꾸지 않는다" 로
 * 못박았다.
 */
let onUnauthorized: (() => void) | null = null
export const setUnauthorizedHandler = (fn: (() => void) | null) => { onUnauthorized = fn }

/** CSRF 토큰을 아직 못 받았을 때 세션을 먼저 받아 오는 함수. AuthProvider 가 건다. */
let ensureCsrf: (() => Promise<void>) | null = null
export const setCsrfLoader = (fn: (() => Promise<void>) | null) => { ensureCsrf = fn }

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
      // 실서버 경로와 같은 처리를 태운다. 안 그러면 Mock 모드(기본값)에서는
      // 세션이 끊겨도 화면이 로그인으로 안 가고 오류 카드만 뜬다.
      if (value.status === 401) { csrf = null; onUnauthorized?.() }
      throw new ApiFailure(value.status, value.code, value.message, value.field)
    }
    return value as T
  }

  const method = init?.method ?? 'GET'
  // 토큰 없이 바꾸는 요청을 보내면 서버가 403 CSRF_INVALID 로 거절한다.
  // 새로고침 직후 곧바로 제출하는 경우가 그렇다 — 먼저 세션을 받아 온다.
  if (!csrf && method !== 'GET' && ensureCsrf) {
    try { await ensureCsrf() } catch { /* 실패해도 아래에서 서버 응답으로 판단한다 */ }
  }
  const res = await fetch(`${BASE}${path}`, {
    ...init,
    // 세션 쿠키(JSESSIONID)를 실어 보낸다. 없으면 모든 보호 API 가 401 이다.
    credentials: 'include',
    headers: {
      // FormData 일 때는 boundary 를 브라우저가 정해야 하므로 Content-Type 을 넣지 않는다.
      ...(init?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      // 바꾸는 요청에만 CSRF 토큰을 싣는다. 가입·로그인·로그아웃도 포함이다.
      ...(csrf && method !== 'GET' ? { 'X-CSRF-TOKEN': csrf } : {}),
      ...init?.headers,
    },
  })

  if (res.status === 401) {
    // 06:284 — 401 은 로그인 화면으로 전환하라는 신호다. AI 작업 실패가 아니다.
    csrf = null
    onUnauthorized?.()
  }
  if (res.status === 403) {
    // 06:260 — CSRF 오류면 세션·토큰을 다시 확인한다. 요청을 자동 재전송하지는
    // 않는다. 사용자가 입력을 유지한 채 다시 누르면 새 토큰으로 나간다.
    csrf = null
    if (ensureCsrf) { try { await ensureCsrf() } catch { /* 무시 */ } }
  }
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
  // #42 의 PUT /trips/{tripId}/packing-layout (06 29번). S-12 를 붙일 때 쓴다.
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  del: (path: string) => request<void>(path, { method: 'DELETE' }),
}
