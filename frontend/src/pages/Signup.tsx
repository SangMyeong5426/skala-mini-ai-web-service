import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context'
import { AuthShell, Field } from '../components/AuthShell'
import { ApiFailure } from '../api/client'

/**
 * 회원가입.
 *
 * 입력은 <b>넷뿐이다</b> — 아이디 · 비밀번호 · 닉네임 · 이메일.
 * 팀이 정한 범위이므로 임의로 늘리지 않는다.
 *
 * 서버가 어느 칸이 잘못됐는지 `field` 로 알려주면(06 오류 봉투) 그 칸 아래에
 * 붙인다. 폼 맨 위에만 적으면 어디를 고쳐야 하는지 찾아야 한다.
 */
export default function Signup() {
  const { signup } = useAuth()
  const nav = useNavigate()

  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [field, setField] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setField(null)
    setBusy(true)
    try {
      await signup({ nickname, loginId, password, email })
      // 06: "가입만으로 인증 세션을 만들지 않으며 S-00 로그인 모드로 이동한다".
      // 아이디를 넘겨서 다시 입력하지 않게 한다.
      nav('/login', { replace: true, state: { justSignedUp: true, loginId } })
    } catch (err) {
      setError(err instanceof Error ? err.message : '가입하지 못했습니다.')
      if (err instanceof ApiFailure && err.field) setField(err.field)
    } finally {
      setBusy(false)
    }
  }

  const msgFor = (name: string) => (field === name ? error : null)

  return (
    <AuthShell
      title="계정을 만들어 주세요"
      sub="아이디·비밀번호·닉네임·이메일만 받아요."
      foot={<>이미 계정이 있나요? <Link to="/login">로그인</Link></>}
    >
      <form className="auth-form" onSubmit={submit} noValidate>
        <Field label="아이디" htmlFor="loginId" hint={msgFor('loginId') ?? '영문 소문자·숫자·밑줄 4~30자.'}>
          <input
            id="loginId" name="username" autoComplete="username" autoFocus
            value={loginId} onChange={(e) => setLoginId(e.target.value)}
          />
        </Field>

        <Field label="비밀번호" htmlFor="password" hint={msgFor('password') ?? '8자 이상.'}>
          <input
            id="password" name="password" type="password" autoComplete="new-password"
            value={password} onChange={(e) => setPassword(e.target.value)}
          />
        </Field>

        <Field label="닉네임" htmlFor="nickname" hint={msgFor('nickname') ?? '화면에 표시되는 이름이에요. 2~50자.'}>
          <input
            id="nickname" name="nickname" autoComplete="nickname"
            value={nickname} onChange={(e) => setNickname(e.target.value)}
          />
        </Field>

        <Field label="이메일" htmlFor="email" hint={msgFor('email') ?? undefined}>
          <input
            id="email" name="email" type="email" autoComplete="email"
            value={email} onChange={(e) => setEmail(e.target.value)}
          />
        </Field>

        {error && !field && <p className="auth-error" role="alert">{error}</p>}

        <button type="submit" className="btn-cta btn-block" disabled={busy}>
          {busy ? '만드는 중…' : '가입하기'}
        </button>
      </form>
    </AuthShell>
  )
}
