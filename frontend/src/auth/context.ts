import { createContext, useContext } from 'react'
import type { SignupRequest, User } from '../types/api'

/**
 * 로그인 상태의 <b>모양과 통로</b>.
 *
 * Provider(AuthContext.tsx)와 따로 둔다. 한 파일에서 컴포넌트와 훅을 같이
 * 내보내면 Vite 의 Fast Refresh 가 그 파일을 통째로 다시 불러서, 편집할 때마다
 * 로그인 상태가 날아간다.
 */
export interface AuthState {
  user: User | null
  /** 첫 세션 조회가 끝나기 전 — 이때 판단하면 로그인한 사람도 튕겨 나간다 */
  loading: boolean
  login: (loginId: string, password: string) => Promise<void>
  /** 06: 가입만으로 세션을 만들지 않는다. 끝나면 로그인 화면으로 보낸다 */
  signup: (input: SignupRequest) => Promise<void>
  logout: () => Promise<void>
}

export const AuthCtx = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const v = useContext(AuthCtx)
  if (!v) throw new Error('useAuth 는 AuthProvider 안에서만 쓸 수 있습니다')
  return v
}
