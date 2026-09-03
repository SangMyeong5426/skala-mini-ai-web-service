import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ApiFailure } from '../api/client'
import { useAuth } from '../auth/context'
import { AuthShell, Field } from '../components/AuthShell'
import { USE_MOCK } from '../api/mock'
import { LOGIN_ID_RE, PASSWORD_MIN } from '../types/api'

/**
 * S-00 로그인 · 회원가입.
 *
 * <b>한 화면에서 모드를 전환한다.</b> 03-wireframe 이 <i>"경로 `/login`.
 * 로그인 모드와 회원가입 모드를 한 화면에서 전환"</i> 으로 못박았고,
 * <i>"로그인·회원가입은 S-00 `/login` 에서만 제공한다"</i> 고 덧붙였다.
 * 그래서 `/signup` 라우트를 두지 않는다.
 *
 * 가입은 <b>자동 로그인하지 않는다</b>(06). 끝나면 아이디만 남기고 비밀번호를
 * 비운 뒤 로그인 모드로 돌아온다.
 *
 * 서버가 알려준 필드 오류(`field`)는 그 칸 아래에 <b>오류 모양</b>으로 붙이고
 * `aria-invalid` 를 준다. 회색 안내문으로 바꿔치기하면 무엇이 잘못됐는지
 * 화면에도 스크린리더에도 남지 않는다.
 */
type Mode = 'login' | 'signup'

export default function Login() {
  const { user, loading, login, signup } = useAuth()
  const nav = useNavigate()
  const loc = useLocation()
  const from = (loc.state as { from?: string } | null)?.from ?? '/trips'

  const [mode, setMode] = useState<Mode>('login')
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [email, setEmail] = useState('')
  const [showPw, setShowPw] = useState(false)

  const [error, setError] = useState<string | null>(null)
  const [field, setField] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // 03: "로그인된 사용자가 S-00 에 오면 S-01 로 이동"
  useEffect(() => {
    if (!loading && user) nav('/trips', { replace: true })
  }, [loading, user, nav])

  const swap = (next: Mode) => {
    setMode(next)
    setError(null); setField(null); setNotice(null)
    setPassword('')
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null); setField(null); setNotice(null)

    // 06 의 형식 규칙을 먼저 걸러 왕복을 줄인다. 최종 판정은 서버가 한다.
    if (!LOGIN_ID_RE.test(loginId.trim().toLowerCase())) {
      setField('loginId'); setError('아이디는 영문 소문자·숫자·밑줄 4~30자입니다.')
      return
    }
    if (password.length < PASSWORD_MIN) {
      setField('password'); setError(`비밀번호는 ${PASSWORD_MIN}자 이상이어야 합니다.`)
      return
    }

    setBusy(true)
    try {
      if (mode === 'login') {
        await login(loginId, password)
        nav(from, { replace: true })
      } else {
        await signup({ nickname, loginId, password, email })
        // 06: 가입만으로 세션을 만들지 않는다. 아이디만 남기고 로그인 모드로.
        setMode('login')
        setPassword('')
        setNotice('가입이 끝났어요. 이제 로그인해 주세요.')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '요청을 처리하지 못했습니다.')
      if (err instanceof ApiFailure && err.field) setField(err.field)
    } finally {
      setBusy(false)
    }
  }

  const isLogin = mode === 'login'
  const errFor = (name: string) => (field === name ? error : null)

  return (
    <AuthShell
      title={isLogin ? '다시 오셨네요' : '계정을 만들어 주세요'}
      sub={isLogin
        ? '아이디로 로그인하고 여행 준비를 이어가세요.'
        : '아이디·비밀번호·닉네임·이메일만 받아요.'}
      foot={isLogin ? (
        <>아직 계정이 없나요?{' '}
          <button type="button" className="link-btn" onClick={() => swap('signup')}>회원가입으로 이동</button>
        </>
      ) : (
        <>이미 계정이 있나요?{' '}
          <button type="button" className="link-btn" onClick={() => swap('login')}>로그인으로 돌아가기</button>
        </>
      )}
    >
      {notice && <p className="auth-note" role="status">{notice}</p>}

      <form className="auth-form" onSubmit={submit} noValidate>
        {!isLogin && (
          <Field label="닉네임" htmlFor="nickname" error={errFor('nickname')} hint="화면에 표시되는 이름이에요. 2~50자.">
            <input
              id="nickname" name="nickname" autoComplete="nickname"
              value={nickname} onChange={(e) => setNickname(e.target.value)}
            />
          </Field>
        )}

        <Field label="아이디" htmlFor="loginId" error={errFor('loginId')} hint="영문 소문자·숫자·밑줄 4~30자.">
          <input
            id="loginId" name="username" autoComplete="username" autoFocus
            value={loginId} onChange={(e) => setLoginId(e.target.value)}
          />
        </Field>

        <Field label="비밀번호" htmlFor="password" error={errFor('password')} hint={isLogin ? undefined : '8자 이상.'}>
          <div className="pw">
            <input
              id="password" name="password"
              aria-invalid={errFor('password') ? true : undefined}
              aria-describedby="password-msg"
              className={errFor('password') ? 'is-bad' : undefined}
              type={showPw ? 'text' : 'password'}
              autoComplete={isLogin ? 'current-password' : 'new-password'}
              value={password} onChange={(e) => setPassword(e.target.value)}
            />
            <button
              type="button" className="pw-toggle"
              onClick={() => setShowPw((v) => !v)}
              aria-pressed={showPw}
            >
              {showPw ? '숨기기' : '표시'}
            </button>
          </div>
        </Field>

        {!isLogin && (
          <Field label="이메일" htmlFor="email" error={errFor('email')}>
            <input
              id="email" name="email" type="email" autoComplete="email"
              value={email} onChange={(e) => setEmail(e.target.value)}
            />
          </Field>
        )}

        {error && !field && <p className="auth-error" role="alert">{error}</p>}

        {/* 세션·CSRF 를 아직 못 받았으면 제출을 막는다. 토큰 없이 나가면 403 이다 */}
        <button type="submit" className="btn-cta btn-block" disabled={busy || loading}>
          {busy ? '확인하는 중…' : isLogin ? '로그인' : '가입하기'}
        </button>
      </form>

      {/*
        * 데모 계정은 Mock 일 때만 보여준다. 실서버에 붙이면 로그인 화면에
        * 비밀번호 문자열이 상시 노출된다. 아이디는 seed.sql 과 같은 값이라
        * Mock ↔ 실서버 전환에서 대본이 바뀌지 않는다.
        */}
      {USE_MOCK && isLogin && (
        <p className="auth-hint">데모 계정 — 아이디 <b>jiwoo28</b> · 비밀번호 <b>skala1234</b></p>
      )}
    </AuthShell>
  )
}
