import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, setCsrfLoader, setCsrfToken, setUnauthorizedHandler } from '../api/client'
import type { AuthUserResponse, SessionResponse, SignupRequest, User } from '../types/api'
import { AuthCtx } from './context'

// StrictMode가 초기 effect를 두 번 확인해도 익명 세션·CSRF 쿠키는 한 요청으로 만든다.
let initialSession: Promise<SessionResponse> | null = null
const loadInitialSession = () => {
  if (!initialSession) {
    initialSession = api.get<SessionResponse>('/auth/session').finally(() => { initialSession = null })
  }
  return initialSession
}

/**
 * 로그인 상태.
 *
 * 라이브러리를 넣지 않는다(CLAUDE.md "기능을 늘리지 않는다"). React 의
 * Context 하나면 된다.
 *
 * <b>인증 정보를 저장하지 않는다.</b> 06 이 서버 세션 + HttpOnly 쿠키로 정했다.
 * JS 는 쿠키를 읽을 수 없고 localStorage 에 둘 것도 없다. 앱이 열릴 때마다
 * `GET /auth/session` 으로 서버에 물어본다.
 *
 * <b>쿠키가 있다고 로그인한 것이 아니다.</b> CSRF 토큰을 주려고 로그인 전에도
 * 익명 세션이 생긴다. 반드시 `authenticated` 를 본다.
 *
 * CSRF 토큰은 client.ts 의 메모리에만 두고, 로그인·로그아웃 뒤에 다시 받는다 —
 * 그때 서버가 세션 ID 를 교체하기 때문이다.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  /** 세션을 다시 읽어 사용자와 CSRF 토큰을 맞춘다 */
  const sync = useCallback(async (): Promise<User | null> => {
    const s = await api.get<SessionResponse>('/auth/session')
    setCsrfToken(s.csrfToken)
    const next = s.authenticated ? s.user : null
    setUser(next)
    return next
  }, [])

  // 세션이 끊기면 사용자 상태를 비운다 → RequireAuth 가 로그인 화면으로 보낸다.
  // 토큰이 없는 채로 나가는 변경 요청은 세션을 먼저 받아 오게 한다.
  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null))
    setCsrfLoader(async () => { await sync() })
    return () => { setUnauthorizedHandler(null); setCsrfLoader(null) }
  }, [sync])

  useEffect(() => {
    let alive = true
    loadInitialSession()
      .then((s) => {
        if (!alive) return
        setCsrfToken(s.csrfToken)
        setUser(s.authenticated ? s.user : null)
      })
      // 세션 조회가 실패하면 미인증으로 둔다. 여기서 막으면 로그인조차 못 한다.
      .catch(() => { if (alive) setUser(null) })
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [])

  const login = useCallback(async (loginId: string, password: string) => {
    await api.post<AuthUserResponse>('/auth/login', { loginId, password })
    // 세션 ID 가 바뀌었으므로 토큰을 새로 받는다
    await sync()
  }, [sync])

  const signup = useCallback(async (input: SignupRequest) => {
    // 06: "가입만으로 인증 세션을 만들지 않으며 S-00 로그인 모드로 이동한다"
    await api.post<AuthUserResponse>('/auth/signup', input)
  }, [])

  const logout = useCallback(async () => {
    // 06: "서버 실패 시 로그아웃 완료로 표시하지 않고 재시도한다" —
    // 그래서 먼저 지우지 않는다. 실패하면 예외가 그대로 올라간다.
    await api.post('/auth/logout')
    setUser(null)
    await sync()
  }, [sync])

  const value = useMemo(
    () => ({ user, loading, login, signup, logout }),
    [user, loading, login, signup, logout],
  )
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>
}
