import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from '../api/client'
import type { AuthResponse, SignupRequest, User } from '../types/api'
import { AuthCtx } from './context'

/**
 * 로그인 상태.
 *
 * 라이브러리를 넣지 않는다(CLAUDE.md "기능을 늘리지 않는다"). React 의
 * Context 하나면 된다.
 *
 * <b>토큰만 저장하고 사용자 정보는 저장하지 않는다.</b> localStorage 의 값은
 * 사용자가 고칠 수 있으므로, 이름·이메일을 거기서 읽으면 화면이 거짓을 보여줄
 * 수 있다. 새로고침할 때마다 토큰으로 다시 물어본다.
 *
 * 토큰을 <b>본문</b>으로 넘기는 것은 Mock 때문이다. Mock 은 요청 헤더를 보지
 * 못한다. 백엔드가 붙으면 client.ts 에서 Authorization 헤더로 옮긴다 —
 * 그때 고칠 곳은 이 파일과 client.ts 두 곳뿐이다.
 */
const TOKEN_KEY = 'jimssa.token'

/** localStorage 는 사생활 보호 모드에서 던질 수 있다. 없으면 없는 대로 돈다. */
function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}
function writeToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* 저장하지 못해도 이번 세션은 동작한다 */
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  // 토큰이 없으면 확인할 것도 없다. 처음부터 false 로 시작해
  // effect 안에서 상태를 되돌리는 일을 만들지 않는다.
  const [loading, setLoading] = useState(() => readToken() !== null)

  // 새로고침 복구 — 토큰이 있으면 누구인지 다시 물어본다
  useEffect(() => {
    const token = readToken()
    if (!token) return
    let alive = true
    api.post<User>('/auth/me', { token })
      .then((u) => { if (alive) setUser(u) })
      .catch(() => { writeToken(null) })   // 만료·위조된 토큰은 버린다
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [])

  const apply = useCallback((r: AuthResponse) => {
    writeToken(r.token)
    setUser(r.user)
  }, [])

  const login = useCallback(async (loginId: string, password: string) => {
    apply(await api.post<AuthResponse>('/auth/login', { loginId, password }))
  }, [apply])

  const signup = useCallback(async (input: SignupRequest) => {
    apply(await api.post<AuthResponse>('/auth/signup', input))
  }, [apply])

  const logout = useCallback(async () => {
    const token = readToken()
    writeToken(null)
    setUser(null)
    // 서버 쪽 정리는 실패해도 화면은 이미 로그아웃 상태다. 되돌리지 않는다.
    try {
      await api.post('/auth/logout', { token })
    } catch {
      /* 무시 */
    }
  }, [])

  const value = useMemo(
    () => ({ user, loading, login, signup, logout }),
    [user, loading, login, signup, logout],
  )
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>
}
