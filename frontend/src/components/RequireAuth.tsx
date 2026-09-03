import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/context'

/**
 * 로그인해야 볼 수 있는 화면을 감싼다.
 *
 * <b>확인이 끝나기 전에는 판단하지 않는다.</b> 새로고침 직후에는 토큰을 아직
 * 확인하는 중이라 user 가 null 인데, 그때 바로 내보내면 로그인한 사람도
 * 로그인 화면으로 튕긴다.
 *
 * 원래 가려던 곳을 state 로 넘겨서 로그인 후 거기로 돌아가게 한다.
 */
export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  const loc = useLocation()

  if (loading) return <div className="auth-wait" aria-busy="true" />
  if (!user) {
    return <Navigate to="/login" replace state={{ from: loc.pathname + loc.search }} />
  }
  return <>{children}</>
}
