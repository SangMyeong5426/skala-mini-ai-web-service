import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context'
import { AuthShell, Field } from '../components/AuthShell'

/**
 * 로그인.
 *
 * 로그인 식별자는 <b>아이디</b>다. 이메일이 아니다.
 *
 * 보호 화면에서 튕겨 온 경우 `location.state.from` 에 원래 가려던 곳이 들어
 * 있다. 로그인 후 <b>거기로 돌려보낸다</b> — 홈으로 보내면 하던 일이 끊긴다.
 */
export default function Login() {
  const { login } = useAuth()
  const nav = useNavigate()
  const loc = useLocation()
  const from = (loc.state as { from?: string } | null)?.from ?? '/trips'

  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(loginId, password)
      nav(from, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '로그인하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell
      title="다시 오셨네요"
      sub="아이디로 로그인하고 여행 준비를 이어가세요."
      foot={<>아직 계정이 없나요? <Link to="/signup">회원가입</Link></>}
    >
      <form className="auth-form" onSubmit={submit} noValidate>
        <Field label="아이디" htmlFor="loginId">
          <input
            id="loginId" name="username" autoComplete="username" autoFocus
            value={loginId} onChange={(e) => setLoginId(e.target.value)}
          />
        </Field>

        <Field label="비밀번호" htmlFor="password">
          <input
            id="password" name="password" type="password" autoComplete="current-password"
            value={password} onChange={(e) => setPassword(e.target.value)}
          />
        </Field>

        {error && <p className="auth-error" role="alert">{error}</p>}

        <button type="submit" className="btn-cta btn-block" disabled={busy}>
          {busy ? '확인하는 중…' : '로그인'}
        </button>
      </form>

      {/*
        * 데모 계정을 화면에 적어 둔다. 발표 자리에서 아이디를 헤매지 않게 하려는
        * 것이고, Mock 전용 값이라 실제 비밀번호가 아니다.
        */}
      <p className="auth-hint">데모 계정 — 아이디 <b>jiwoo</b> · 비밀번호 <b>skala1234</b></p>
    </AuthShell>
  )
}
